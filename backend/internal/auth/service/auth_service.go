package service

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"log/slog"
	"math/big"
	"os"
	"strings"
	"time"

	"github.com/xilo-platform/xilo/internal/auth/model"
	"github.com/xilo-platform/xilo/internal/auth/repository"
	"github.com/xilo-platform/xilo/pkg/hash"
	"github.com/xilo-platform/xilo/pkg/i18n"
	"github.com/xilo-platform/xilo/pkg/jwt"
	"github.com/xilo-platform/xilo/pkg/sms"
	"github.com/xilo-platform/xilo/pkg/validator"
)

const provisionalUsernamePrefix = "tmp_"

var (
	ErrInvalidCredentials = fmt.Errorf("invalid email or password")
	ErrTokenReused        = fmt.Errorf("refresh token already used — all tokens revoked")
	ErrEmailNotVerified   = fmt.Errorf("email not verified")
	ErrInvalidOTP         = fmt.Errorf("invalid or expired OTP")
	ErrOTPRateLimited     = fmt.Errorf("otp already sent, please wait")
)

type UserRepository interface {
	Create(ctx context.Context, req *model.RegisterRequest, passwordHash string) (*model.User, error)
	CreateWithPhone(ctx context.Context, email, username, phone, passwordHash string) (*model.User, error)
	FindByEmail(ctx context.Context, email string) (*model.User, error)
	FindByID(ctx context.Context, id string) (*model.User, error)
	FindByUsername(ctx context.Context, username string) (*model.User, error)
	FindByPhone(ctx context.Context, phone string) (*model.User, error)
	UpdateProfile(ctx context.Context, userID string, req *model.UpdateProfileRequest) (*model.User, error)
	UpdatePhone(ctx context.Context, userID, phone string) error
	SaveRefreshToken(ctx context.Context, userID, family string, tokenHash string, expiresAt time.Time, device *model.DeviceMetadata) error
	FindRefreshToken(ctx context.Context, tokenHash string) (*model.RefreshToken, error)
	RevokeRefreshToken(ctx context.Context, tokenHash string) error
	RevokeTokenFamily(ctx context.Context, family string) error
	ListActiveSessions(ctx context.Context, userID string) ([]model.RefreshToken, error)
	FindSessionByID(ctx context.Context, userID, sessionID string) (*model.RefreshToken, error)
	UpdateSessionLastSeen(ctx context.Context, tokenHash string) error
	SaveSMSOTP(ctx context.Context, phone, code, purpose string, expiresAt time.Time) error
	VerifySMSOTP(ctx context.Context, phone, code, purpose string) (bool, error)
}

type TokenManager interface {
	GenerateAccessToken(userID, username, role string) (string, error)
	GenerateRefreshToken(userID, username, role string) (string, string, error)
	ValidateToken(tokenString string) (*jwt.Claims, error)
}

type AuthService struct {
	repo      UserRepository
	jwtMgr    TokenManager
	smsDriver sms.Driver
}

func NewAuthService(repo UserRepository, jwtMgr TokenManager) *AuthService {
	return &AuthService{repo: repo, jwtMgr: jwtMgr, smsDriver: nil}
}

func NewAuthServiceWithSMS(repo UserRepository, jwtMgr TokenManager, smsDriver sms.Driver) *AuthService {
	return &AuthService{repo: repo, jwtMgr: jwtMgr, smsDriver: smsDriver}
}

func (s *AuthService) Register(ctx context.Context, req *model.RegisterRequest) (*model.AuthResponse, error) {
	if verr := validator.ValidateEmail(req.Email); verr != nil {
		return nil, fmt.Errorf("%s: %s", verr.Field, verr.Message)
	}
	if verrs := validator.ValidatePassword(req.Password); len(verrs) > 0 {
		return nil, fmt.Errorf("password: %s", verrs[0].Message)
	}

	username := strings.TrimSpace(req.Username)
	if username == "" {
		generated, err := generateProvisionalUsername()
		if err != nil {
			return nil, fmt.Errorf("generate username: %w", err)
		}
		username = generated
	} else if verr := validator.ValidateUsername(username); verr != nil {
		return nil, fmt.Errorf("%s: %s", verr.Field, verr.Message)
	} else if strings.HasPrefix(username, provisionalUsernamePrefix) {
		return nil, fmt.Errorf("username: reserved prefix")
	}
	req.Username = username

	passwordHash, err := hash.Hash(req.Password)
	if err != nil {
		return nil, fmt.Errorf("hash password: %w", err)
	}

	user, err := s.repo.Create(ctx, req, passwordHash)
	if err != nil {
		return nil, err
	}

	return s.generateAuthResponse(ctx, user)
}

func (s *AuthService) Login(ctx context.Context, req *model.LoginRequest) (*model.AuthResponse, error) {
	if req.Email == "" || req.Password == "" {
		return nil, ErrInvalidCredentials
	}

	user, err := s.repo.FindByEmail(ctx, req.Email)
	if err != nil {
		if err == repository.ErrUserNotFound {
			return nil, ErrInvalidCredentials
		}
		return nil, err
	}

	if err := hash.Verify(req.Password, user.PasswordHash); err != nil {
		return nil, ErrInvalidCredentials
	}

	return s.generateAuthResponse(ctx, user)
}

func (s *AuthService) Refresh(ctx context.Context, refreshToken string) (*model.AuthResponse, error) {
	tokenHash := repository.HashRefreshToken(refreshToken)

	rt, err := s.repo.FindRefreshToken(ctx, tokenHash)
	if err != nil {
		return nil, ErrInvalidCredentials
	}

	if rt.Revoked {
		if err := s.repo.RevokeTokenFamily(ctx, rt.Family); err != nil {
			return nil, fmt.Errorf("revoke token family: %w", err)
		}
		return nil, ErrTokenReused
	}

	if time.Now().After(rt.ExpiresAt) {
		return nil, ErrInvalidCredentials
	}

	if err := s.repo.RevokeRefreshToken(ctx, tokenHash); err != nil {
		return nil, fmt.Errorf("revoke old refresh token: %w", err)
	}

	user, err := s.repo.FindByID(ctx, rt.UserID)
	if err != nil {
		return nil, err
	}

	return s.generateAuthResponse(ctx, user)
}

func (s *AuthService) Me(ctx context.Context, userID string) (*model.User, error) {
	user, err := s.repo.FindByID(ctx, userID)
	if err != nil {
		return nil, err
	}
	annotateUsernamePending(user)
	return user, nil
}

func (s *AuthService) UpdateProfile(ctx context.Context, userID string, req *model.UpdateProfileRequest) (*model.User, error) {
	if req.PreferredLanguage != "" && !i18n.IsValidLanguage(req.PreferredLanguage) {
		return nil, fmt.Errorf("invalid preferred language code: %s", req.PreferredLanguage)
	}
	if req.PreferredCalendar != "" && !i18n.IsValidCalendarPreference(req.PreferredCalendar) {
		return nil, fmt.Errorf("invalid preferred calendar: %s", req.PreferredCalendar)
	}
	if req.Username != "" {
		username := strings.TrimSpace(req.Username)
		if verr := validator.ValidateUsername(username); verr != nil {
			return nil, fmt.Errorf("%s: %s", verr.Field, verr.Message)
		}
		if strings.HasPrefix(username, provisionalUsernamePrefix) {
			return nil, fmt.Errorf("username: reserved prefix")
		}
		req.Username = username
	}
	user, err := s.repo.UpdateProfile(ctx, userID, req)
	if err != nil {
		return nil, err
	}
	annotateUsernamePending(user)
	return user, nil
}

func (s *AuthService) Logout(ctx context.Context, refreshToken string) error {
	tokenHash := repository.HashRefreshToken(refreshToken)
	return s.repo.RevokeRefreshToken(ctx, tokenHash)
}

func (s *AuthService) ListSessions(ctx context.Context, userID, currentRefreshToken string) ([]model.SessionResponse, error) {
	sessions, err := s.repo.ListActiveSessions(ctx, userID)
	if err != nil {
		return nil, fmt.Errorf("list sessions: %w", err)
	}

	currentFamily := ""
	if currentRefreshToken != "" {
		tokenHash := repository.HashRefreshToken(currentRefreshToken)
		if rt, err := s.repo.FindRefreshToken(ctx, tokenHash); err == nil && rt != nil {
			currentFamily = rt.Family
		}
	}

	result := make([]model.SessionResponse, 0, len(sessions))
	for _, session := range sessions {
		result = append(result, model.SessionResponse{
			ID:         session.ID,
			Family:     session.Family,
			DeviceName: session.DeviceName,
			Platform:   session.Platform,
			UserAgent:  session.UserAgent,
			IP:         session.IP,
			LastSeenAt: session.LastSeenAt,
			CreatedAt:  session.CreatedAt,
			IsCurrent:  currentFamily != "" && session.Family == currentFamily,
		})
	}
	return result, nil
}

func (s *AuthService) RevokeSession(ctx context.Context, userID, sessionID string) error {
	session, err := s.repo.FindSessionByID(ctx, userID, sessionID)
	if err != nil {
		return err
	}
	return s.repo.RevokeTokenFamily(ctx, session.Family)
}

func (s *AuthService) RequestOTP(ctx context.Context, req *model.RequestOTPRequest) error {
	if req.Phone == "" {
		return fmt.Errorf("phone is required")
	}

	if req.Purpose == "" {
		req.Purpose = "auth"
	}

	code, err := generateOTP()
	if err != nil {
		return fmt.Errorf("generate otp: %w", err)
	}

	expiresAt := time.Now().Add(5 * time.Minute)
	if err := s.repo.SaveSMSOTP(ctx, req.Phone, code, req.Purpose, expiresAt); err != nil {
		return fmt.Errorf("save otp: %w", err)
	}

	if s.smsDriver != nil {
		patternCode := os.Getenv("SMS_OTP_PATTERN_CODE")
		varName := os.Getenv("SMS_OTP_PATTERN_VAR")
		if varName == "" {
			varName = "code" // Default backward compatibility, or "verification-code"
		}
		_, err := s.smsDriver.SendPattern(ctx, req.Phone, patternCode, map[string]string{
			varName: code,
		})
		if err != nil {
			slog.Error("send otp sms failed", "error", err)
		}
	}

	return nil
}

func (s *AuthService) VerifyOTPLogin(ctx context.Context, req *model.VerifyOTPLoginRequest) (*model.AuthResponse, error) {
	if req.Phone == "" || req.Code == "" {
		return nil, ErrInvalidOTP
	}

	valid, err := s.repo.VerifySMSOTP(ctx, req.Phone, req.Code, "auth")
	if err != nil {
		return nil, fmt.Errorf("verify otp: %w", err)
	}
	if !valid {
		return nil, ErrInvalidOTP
	}

	user, err := s.repo.FindByPhone(ctx, req.Phone)
	if err != nil {
		if errors.Is(err, repository.ErrUserNotFound) {
			return nil, fmt.Errorf("no account found with this phone number")
		}
		return nil, err
	}

	return s.generateAuthResponse(ctx, user)
}

func (s *AuthService) VerifyOTPRegister(ctx context.Context, req *model.VerifyOTPRegisterRequest) (*model.AuthResponse, error) {
	if req.Phone == "" || req.Code == "" {
		return nil, ErrInvalidOTP
	}

	if req.Email == "" && req.Username == "" {
		return nil, fmt.Errorf("email or username is required for registration")
	}

	if req.Email != "" {
		if verr := validator.ValidateEmail(req.Email); verr != nil {
			return nil, fmt.Errorf("%s: %s", verr.Field, verr.Message)
		}
	}
	if req.Username != "" {
		if verr := validator.ValidateUsername(req.Username); verr != nil {
			return nil, fmt.Errorf("%s: %s", verr.Field, verr.Message)
		}
	}

	valid, err := s.repo.VerifySMSOTP(ctx, req.Phone, req.Code, "auth")
	if err != nil {
		return nil, fmt.Errorf("verify otp: %w", err)
	}
	if !valid {
		return nil, ErrInvalidOTP
	}

	randomPass, _ := generateOTP()
	passwordHash, err := hash.Hash(randomPass + randomPass)
	if err != nil {
		return nil, fmt.Errorf("hash password: %w", err)
	}

	email := req.Email
	if email == "" {
		email = req.Phone + "@sms.xilo.local"
	}
	username := strings.TrimSpace(req.Username)
	if username == "" {
		generated, err := generateProvisionalUsername()
		if err != nil {
			return nil, fmt.Errorf("generate username: %w", err)
		}
		username = generated
	} else if strings.HasPrefix(username, provisionalUsernamePrefix) {
		return nil, fmt.Errorf("username: reserved prefix")
	}

	user, err := s.repo.CreateWithPhone(ctx, email, username, req.Phone, passwordHash)
	if err != nil {
		return nil, err
	}

	return s.generateAuthResponse(ctx, user)
}

func (s *AuthService) generateAuthResponse(ctx context.Context, user *model.User) (*model.AuthResponse, error) {
	annotateUsernamePending(user)

	accessToken, err := s.jwtMgr.GenerateAccessToken(user.ID, user.Username, user.Role)
	if err != nil {
		return nil, fmt.Errorf("generate access token: %w", err)
	}

	refreshToken, jti, err := s.jwtMgr.GenerateRefreshToken(user.ID, user.Username, user.Role)
	if err != nil {
		return nil, fmt.Errorf("generate refresh token: %w", err)
	}

	tokenHash := repository.HashRefreshToken(refreshToken)
	expiresAt := time.Now().Add(7 * 24 * time.Hour)
	device := DeviceMetadataFromContext(ctx)
	if err := s.repo.SaveRefreshToken(ctx, user.ID, jti, tokenHash, expiresAt, device); err != nil {
		return nil, fmt.Errorf("save refresh token: %w", err)
	}

	return &model.AuthResponse{
		AccessToken:  accessToken,
		RefreshToken: refreshToken,
		ExpiresIn:    900,
		User:         user,
	}, nil
}

func annotateUsernamePending(user *model.User) {
	if user == nil {
		return
	}
	user.UsernamePending = strings.HasPrefix(user.Username, provisionalUsernamePrefix)
}

func generateProvisionalUsername() (string, error) {
	buf := make([]byte, 6)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	return provisionalUsernamePrefix + hex.EncodeToString(buf), nil
}

func generateOTP() (string, error) {
	n, err := rand.Int(rand.Reader, big.NewInt(900000))
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%06d", n.Int64()+100000), nil
}
