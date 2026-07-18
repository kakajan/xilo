package repository

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/jmoiron/sqlx"
	"github.com/xilo-platform/xilo/internal/auth/model"
)

var (
	ErrUserNotFound        = errors.New("user not found")
	ErrEmailExists         = errors.New("email already exists")
	ErrUsernameExists      = errors.New("username already exists")
	ErrRefreshTokenRevoked = errors.New("refresh token revoked")
	ErrSessionNotFound     = errors.New("session not found")
)

type UserRepo struct {
	db *sqlx.DB
}

func NewUserRepo(db *sqlx.DB) *UserRepo {
	return &UserRepo{db: db}
}

func (r *UserRepo) Create(ctx context.Context, req *model.RegisterRequest, passwordHash string) (*model.User, error) {
	var user model.User
	err := r.db.GetContext(ctx, &user, `
		INSERT INTO users (email, username, password_hash)
		VALUES ($1, $2, $3)
		RETURNING id, email, username, phone, password_hash,
		          COALESCE(display_name, '') AS display_name,
		          COALESCE(avatar_url, '') AS avatar_url,
		          COALESCE(bio, '') AS bio,
		          role, email_verified, preferred_language, preferred_calendar, created_at, updated_at
	`, req.Email, req.Username, passwordHash)
	if err != nil {
		if isUniqueViolation(err) {
			return nil, ErrEmailExists
		}
		return nil, fmt.Errorf("insert user: %w", err)
	}
	return &user, nil
}

func (r *UserRepo) CreateWithPhone(ctx context.Context, email, username, phone, passwordHash string) (*model.User, error) {
	var user model.User
	err := r.db.GetContext(ctx, &user, `
		INSERT INTO users (email, username, phone, password_hash)
		VALUES ($1, $2, $3, $4)
		RETURNING id, email, username, phone, password_hash,
		          COALESCE(display_name, '') AS display_name,
		          COALESCE(avatar_url, '') AS avatar_url,
		          COALESCE(bio, '') AS bio,
		          role, email_verified, preferred_language, preferred_calendar, created_at, updated_at
	`, email, username, phone, passwordHash)
	if err != nil {
		if isUniqueViolation(err) {
			return nil, ErrEmailExists
		}
		return nil, fmt.Errorf("insert user: %w", err)
	}
	return &user, nil
}

func (r *UserRepo) FindByEmail(ctx context.Context, email string) (*model.User, error) {
	var user model.User
	err := r.db.GetContext(ctx, &user, `
		SELECT id, email, username, phone, password_hash,
		       COALESCE(display_name, '') AS display_name,
		       COALESCE(avatar_url, '') AS avatar_url,
		       COALESCE(bio, '') AS bio,
		       role, email_verified, preferred_language, preferred_calendar, created_at, updated_at
		FROM users
		WHERE email = $1 AND deleted_at IS NULL
	`, email)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrUserNotFound
		}
		return nil, fmt.Errorf("find user by email: %w", err)
	}
	return &user, nil
}

func (r *UserRepo) FindByID(ctx context.Context, id string) (*model.User, error) {
	var user model.User
	err := r.db.GetContext(ctx, &user, `
		SELECT id, email, username, phone, password_hash,
		       COALESCE(display_name, '') AS display_name,
		       COALESCE(avatar_url, '') AS avatar_url,
		       COALESCE(bio, '') AS bio,
		       role, email_verified, preferred_language, preferred_calendar, created_at, updated_at
		FROM users
		WHERE id = $1 AND deleted_at IS NULL
	`, id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrUserNotFound
		}
		return nil, fmt.Errorf("find user by id: %w", err)
	}
	return &user, nil
}

func (r *UserRepo) FindByUsername(ctx context.Context, username string) (*model.User, error) {
	var user model.User
	err := r.db.GetContext(ctx, &user, `
		SELECT id, email, username, phone, password_hash,
		       COALESCE(display_name, '') AS display_name,
		       COALESCE(avatar_url, '') AS avatar_url,
		       COALESCE(bio, '') AS bio,
		       role, email_verified, preferred_language, preferred_calendar, created_at, updated_at
		FROM users
		WHERE username = $1 AND deleted_at IS NULL
	`, username)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrUserNotFound
		}
		return nil, fmt.Errorf("find user by username: %w", err)
	}
	return &user, nil
}

func (r *UserRepo) UpdateProfile(ctx context.Context, userID string, req *model.UpdateProfileRequest) (*model.User, error) {
	var user model.User
	err := r.db.GetContext(ctx, &user, `
		UPDATE users
		SET display_name = COALESCE(NULLIF($2, ''), display_name),
		    bio = COALESCE(NULLIF($3, ''), bio),
		    avatar_url = COALESCE(NULLIF($4, ''), avatar_url),
		    preferred_language = COALESCE(NULLIF($5, ''), preferred_language),
		    preferred_calendar = COALESCE(NULLIF($6, ''), preferred_calendar),
		    username = COALESCE(NULLIF($7, ''), username),
		    updated_at = NOW()
		WHERE id = $1 AND deleted_at IS NULL
		RETURNING id, email, username, phone, password_hash,
		          COALESCE(display_name, '') AS display_name,
		          COALESCE(avatar_url, '') AS avatar_url,
		          COALESCE(bio, '') AS bio,
		          role, email_verified, preferred_language, preferred_calendar, created_at, updated_at
	`, userID, req.DisplayName, req.Bio, req.AvatarURL, req.PreferredLanguage, req.PreferredCalendar, req.Username)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrUserNotFound
		}
		if isUniqueViolation(err) {
			return nil, ErrUsernameExists
		}
		return nil, fmt.Errorf("update user profile: %w", err)
	}
	return &user, nil
}

func (r *UserRepo) SaveRefreshToken(
	ctx context.Context,
	userID, family string,
	tokenHash string,
	expiresAt time.Time,
	device *model.DeviceMetadata,
) error {
	var deviceName, platform, userAgent, ip *string
	if device != nil {
		deviceName = device.DeviceName
		platform = device.Platform
		userAgent = device.UserAgent
		ip = device.IP
	}
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO refresh_tokens (
			user_id, token_hash, family, expires_at,
			device_name, platform, user_agent, ip, last_seen_at
		)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, NOW())
	`, userID, tokenHash, family, expiresAt, deviceName, platform, userAgent, ip)
	return err
}

func (r *UserRepo) ListActiveSessions(ctx context.Context, userID string) ([]model.RefreshToken, error) {
	var sessions []model.RefreshToken
	err := r.db.SelectContext(ctx, &sessions, `
		SELECT DISTINCT ON (family)
		       id, user_id, token_hash, family, expires_at, revoked,
		       device_name, platform, user_agent, ip, last_seen_at, created_at
		FROM refresh_tokens
		WHERE user_id = $1
		  AND revoked = FALSE
		  AND expires_at > NOW()
		ORDER BY family, created_at DESC
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("list active sessions: %w", err)
	}
	if sessions == nil {
		sessions = []model.RefreshToken{}
	}
	return sessions, nil
}

func (r *UserRepo) FindSessionByID(ctx context.Context, userID, sessionID string) (*model.RefreshToken, error) {
	var session model.RefreshToken
	err := r.db.GetContext(ctx, &session, `
		SELECT id, user_id, token_hash, family, expires_at, revoked,
		       device_name, platform, user_agent, ip, last_seen_at, created_at
		FROM refresh_tokens
		WHERE id = $1 AND user_id = $2
	`, sessionID, userID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrSessionNotFound
		}
		return nil, fmt.Errorf("find session by id: %w", err)
	}
	return &session, nil
}

func (r *UserRepo) UpdateSessionLastSeen(ctx context.Context, tokenHash string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE refresh_tokens
		SET last_seen_at = NOW()
		WHERE token_hash = $1 AND revoked = FALSE
	`, tokenHash)
	return err
}

func (r *UserRepo) FindRefreshToken(ctx context.Context, tokenHash string) (*model.RefreshToken, error) {
	var rt model.RefreshToken
	err := r.db.GetContext(ctx, &rt, `
		SELECT id, user_id, token_hash, family, expires_at, revoked,
		       device_name, platform, user_agent, ip, last_seen_at, created_at
		FROM refresh_tokens
		WHERE token_hash = $1
	`, tokenHash)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrRefreshTokenRevoked
		}
		return nil, fmt.Errorf("find refresh token: %w", err)
	}
	return &rt, nil
}

func (r *UserRepo) RevokeRefreshToken(ctx context.Context, tokenHash string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE refresh_tokens SET revoked = TRUE WHERE token_hash = $1
	`, tokenHash)
	return err
}

func (r *UserRepo) RevokeTokenFamily(ctx context.Context, family string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE refresh_tokens SET revoked = TRUE WHERE family = $1
	`, family)
	return err
}

func HashRefreshToken(token string) string {
	h := sha256.Sum256([]byte(token))
	return hex.EncodeToString(h[:])
}

func isUniqueViolation(err error) bool {
	if err == nil {
		return false
	}
	return strings.Contains(err.Error(), "duplicate key") || strings.Contains(err.Error(), "unique constraint")
}

func (r *UserRepo) FindByPhone(ctx context.Context, phone string) (*model.User, error) {
	var user model.User
	err := r.db.GetContext(ctx, &user, `
		SELECT id, email, username, phone, password_hash,
		       COALESCE(display_name, '') AS display_name,
		       COALESCE(avatar_url, '') AS avatar_url,
		       COALESCE(bio, '') AS bio,
		       role, email_verified, preferred_language, preferred_calendar, created_at, updated_at
		FROM users
		WHERE phone = $1 AND deleted_at IS NULL
	`, phone)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrUserNotFound
		}
		return nil, fmt.Errorf("find user by phone: %w", err)
	}
	return &user, nil
}

func (r *UserRepo) SaveSMSOTP(ctx context.Context, phone, code, purpose string, expiresAt time.Time) error {
	_, err := r.db.ExecContext(ctx, `
		INSERT INTO sms_otps (phone, code, purpose, expires_at)
		VALUES ($1, $2, $3, $4)
	`, phone, code, purpose, expiresAt)
	return err
}

func (r *UserRepo) VerifySMSOTP(ctx context.Context, phone, code, purpose string) (bool, error) {
	var otp struct {
		ID        string    `db:"id"`
		ExpiresAt time.Time `db:"expires_at"`
		Used      bool      `db:"used"`
		Attempts  int       `db:"attempts"`
	}
	err := r.db.GetContext(ctx, &otp, `
		SELECT id, expires_at, used, attempts
		FROM sms_otps
		WHERE phone = $1 AND code = $2 AND purpose = $3
		ORDER BY created_at DESC
		LIMIT 1
	`, phone, code, purpose)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return false, nil
		}
		return false, fmt.Errorf("verify sms otp: %w", err)
	}

	if otp.Used {
		return false, nil
	}

	if time.Now().After(otp.ExpiresAt) {
		return false, nil
	}

	_, err = r.db.ExecContext(ctx, `
		UPDATE sms_otps SET used = TRUE, attempts = attempts + 1 WHERE id = $1
	`, otp.ID)
	if err != nil {
		return false, fmt.Errorf("mark sms otp used: %w", err)
	}

	return true, nil
}

func (r *UserRepo) UpdatePhone(ctx context.Context, userID, phone string) error {
	_, err := r.db.ExecContext(ctx, `
		UPDATE users SET phone = $2, updated_at = NOW() WHERE id = $1
	`, userID, phone)
	return err
}
