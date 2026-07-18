package service

import (
	"context"
	"strings"
	"testing"
	"time"

	"github.com/xilo-platform/xilo/internal/auth/model"
	"github.com/xilo-platform/xilo/internal/auth/repository"
	"github.com/xilo-platform/xilo/pkg/jwt"
)

type mockUserRepo struct {
	users  map[string]*model.User
	tokens map[string]*model.RefreshToken
}

func newMockUserRepo() *mockUserRepo {
	return &mockUserRepo{
		users:  make(map[string]*model.User),
		tokens: make(map[string]*model.RefreshToken),
	}
}

func (m *mockUserRepo) Create(ctx context.Context, req *model.RegisterRequest, passwordHash string) (*model.User, error) {
	user := &model.User{
		ID:       "test-id",
		Email:    req.Email,
		Username: req.Username,
		Role:     "reader",
	}
	m.users[user.ID] = user
	return user, nil
}

func (m *mockUserRepo) FindByEmail(ctx context.Context, email string) (*model.User, error) {
	for _, u := range m.users {
		if u.Email == email {
			return u, nil
		}
	}
	return nil, nil
}

func (m *mockUserRepo) FindByID(ctx context.Context, id string) (*model.User, error) {
	u, ok := m.users[id]
	if !ok {
		return nil, nil
	}
	return u, nil
}

func (m *mockUserRepo) FindByUsername(ctx context.Context, username string) (*model.User, error) { return nil, nil }
func (m *mockUserRepo) FindByPhone(ctx context.Context, phone string) (*model.User, error)        { return nil, nil }
func (m *mockUserRepo) CreateWithPhone(ctx context.Context, email, username, phone, passwordHash string) (*model.User, error) { return nil, nil }
func (m *mockUserRepo) UpdatePhone(ctx context.Context, userID, phone string) error                { return nil }
func (m *mockUserRepo) UpdateProfile(ctx context.Context, userID string, req *model.UpdateProfileRequest) (*model.User, error) { return nil, nil }
func (m *mockUserRepo) SaveRefreshToken(ctx context.Context, userID, family string, tokenHash string, expiresAt time.Time, device *model.DeviceMetadata) error {
	m.tokens[tokenHash] = &model.RefreshToken{
		ID:         tokenHash,
		UserID:     userID,
		TokenHash:  tokenHash,
		Family:     family,
		ExpiresAt:  expiresAt,
		DeviceName: deviceDeviceName(device),
		Platform:   devicePlatform(device),
		LastSeenAt: time.Now(),
		CreatedAt:  time.Now(),
	}
	return nil
}
func (m *mockUserRepo) FindRefreshToken(ctx context.Context, tokenHash string) (*model.RefreshToken, error) {
	rt, ok := m.tokens[tokenHash]
	if !ok {
		return nil, nil
	}
	return rt, nil
}
func (m *mockUserRepo) RevokeRefreshToken(ctx context.Context, tokenHash string) error {
	if rt, ok := m.tokens[tokenHash]; ok {
		rt.Revoked = true
	}
	return nil
}
func (m *mockUserRepo) RevokeTokenFamily(ctx context.Context, family string) error {
	for _, rt := range m.tokens {
		if rt.Family == family {
			rt.Revoked = true
		}
	}
	return nil
}
func (m *mockUserRepo) ListActiveSessions(ctx context.Context, userID string) ([]model.RefreshToken, error) {
	seen := map[string]struct{}{}
	sessions := make([]model.RefreshToken, 0)
	for _, rt := range m.tokens {
		if rt.UserID != userID || rt.Revoked || time.Now().After(rt.ExpiresAt) {
			continue
		}
		if _, ok := seen[rt.Family]; ok {
			continue
		}
		seen[rt.Family] = struct{}{}
		sessions = append(sessions, *rt)
	}
	return sessions, nil
}
func (m *mockUserRepo) FindSessionByID(ctx context.Context, userID, sessionID string) (*model.RefreshToken, error) {
	for _, rt := range m.tokens {
		if rt.ID == sessionID && rt.UserID == userID {
			return rt, nil
		}
	}
	return nil, repository.ErrSessionNotFound
}
func (m *mockUserRepo) UpdateSessionLastSeen(ctx context.Context, tokenHash string) error { return nil }
func (m *mockUserRepo) SaveSMSOTP(ctx context.Context, phone, code, purpose string, expiresAt time.Time) error { return nil }
func (m *mockUserRepo) VerifySMSOTP(ctx context.Context, phone, code, purpose string) (bool, error) { return true, nil }

type mockJWTManager struct{}

func (m *mockJWTManager) GenerateAccessToken(userID, username, role string) (string, error) {
	return "access-token", nil
}

func (m *mockJWTManager) GenerateRefreshToken(userID, username, role string) (string, string, error) {
	return "refresh-token", "jti", nil
}

func (m *mockJWTManager) ValidateToken(tokenString string) (*jwt.Claims, error) {
	return &jwt.Claims{UserID: "test-id", Role: "reader"}, nil
}

func TestRegister_ValidInput(t *testing.T) {
	repo := newMockUserRepo()
	jwtMgr := &mockJWTManager{}
	svc := NewAuthService(repo, jwtMgr)

	resp, err := svc.Register(context.Background(), &model.RegisterRequest{
		Email:    "test@example.com",
		Username: "testuser",
		Password: "Str0ng!Pass",
	})
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if resp.AccessToken != "access-token" {
		t.Errorf("expected access-token, got %s", resp.AccessToken)
	}
	if resp.User.Email != "test@example.com" {
		t.Errorf("expected email test@example.com, got %s", resp.User.Email)
	}
}

func TestRegister_WithoutUsername_AssignsProvisional(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewAuthService(repo, &mockJWTManager{})

	resp, err := svc.Register(context.Background(), &model.RegisterRequest{
		Email:    "auto@example.com",
		Password: "Str0ng!Pass",
	})
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if resp.User == nil || !strings.HasPrefix(resp.User.Username, "tmp_") {
		t.Fatalf("expected provisional tmp_ username, got %#v", resp.User)
	}
	if !resp.User.UsernamePending {
		t.Fatal("expected username_pending true")
	}
}

func TestRegister_InvalidEmail(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewAuthService(repo, &mockJWTManager{})

	_, err := svc.Register(context.Background(), &model.RegisterRequest{
		Email:    "invalid",
		Username: "testuser",
		Password: "Str0ng!Pass",
	})
	if err == nil {
		t.Fatal("expected error for invalid email")
	}
}

func TestRegister_WeakPassword(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewAuthService(repo, &mockJWTManager{})

	_, err := svc.Register(context.Background(), &model.RegisterRequest{
		Email:    "test@example.com",
		Username: "testuser",
		Password: "weak",
	})
	if err == nil {
		t.Fatal("expected error for weak password")
	}
}

func deviceDeviceName(device *model.DeviceMetadata) *string {
	if device == nil {
		return nil
	}
	return device.DeviceName
}

func devicePlatform(device *model.DeviceMetadata) *string {
	if device == nil {
		return nil
	}
	return device.Platform
}

func TestListSessions_MarksCurrentFamily(t *testing.T) {
	repo := newMockUserRepo()
	repo.users["user-1"] = &model.User{ID: "user-1", Email: "a@example.com", Username: "a", Role: "reader"}
	repo.tokens[repository.HashRefreshToken("current-token")] = &model.RefreshToken{
		ID:         "session-current",
		UserID:     "user-1",
		TokenHash:  repository.HashRefreshToken("current-token"),
		Family:     "family-current",
		ExpiresAt:  time.Now().Add(time.Hour),
		CreatedAt:  time.Now(),
		LastSeenAt: time.Now(),
	}
	repo.tokens["other-hash"] = &model.RefreshToken{
		ID:        "session-other",
		UserID:    "user-1",
		TokenHash: "other-hash",
		Family:    "family-other",
		ExpiresAt: time.Now().Add(time.Hour),
		CreatedAt: time.Now(),
		LastSeenAt: time.Now(),
	}

	svc := NewAuthService(repo, &mockJWTManager{})
	sessions, err := svc.ListSessions(context.Background(), "user-1", "current-token")
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if len(sessions) != 2 {
		t.Fatalf("expected 2 sessions, got %d", len(sessions))
	}

	currentMarked := false
	for _, session := range sessions {
		if session.ID == "session-current" && session.IsCurrent {
			currentMarked = true
		}
		if session.ID == "session-other" && session.IsCurrent {
			t.Fatal("other session should not be current")
		}
	}
	if !currentMarked {
		t.Fatal("expected current session to be marked")
	}
}

func TestRevokeSession_RevokesFamily(t *testing.T) {
	repo := newMockUserRepo()
	repo.tokens["hash-1"] = &model.RefreshToken{
		ID:        "session-1",
		UserID:    "user-1",
		TokenHash: "hash-1",
		Family:    "family-1",
		ExpiresAt: time.Now().Add(time.Hour),
	}
	repo.tokens["hash-2"] = &model.RefreshToken{
		ID:        "session-2",
		UserID:    "user-1",
		TokenHash: "hash-2",
		Family:    "family-1",
		ExpiresAt: time.Now().Add(time.Hour),
	}

	svc := NewAuthService(repo, &mockJWTManager{})
	if err := svc.RevokeSession(context.Background(), "user-1", "session-1"); err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if !repo.tokens["hash-1"].Revoked || !repo.tokens["hash-2"].Revoked {
		t.Fatal("expected all tokens in family to be revoked")
	}
}

func TestRevokeSession_NotFound(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewAuthService(repo, &mockJWTManager{})
	err := svc.RevokeSession(context.Background(), "user-1", "missing")
	if err != repository.ErrSessionNotFound {
		t.Fatalf("expected ErrSessionNotFound, got %v", err)
	}
}
