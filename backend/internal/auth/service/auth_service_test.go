package service

import (
	"context"
	"testing"
	"time"

	"github.com/xilo-platform/xilo/internal/auth/model"
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
func (m *mockUserRepo) SaveRefreshToken(ctx context.Context, userID, family string, tokenHash string, expiresAt time.Time) error { return nil }
func (m *mockUserRepo) FindRefreshToken(ctx context.Context, tokenHash string) (*model.RefreshToken, error) { return nil, nil }
func (m *mockUserRepo) RevokeRefreshToken(ctx context.Context, tokenHash string) error { return nil }
func (m *mockUserRepo) RevokeTokenFamily(ctx context.Context, family string) error     { return nil }
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
