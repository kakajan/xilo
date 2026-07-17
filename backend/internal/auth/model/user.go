package model

import (
	"time"
)

type User struct {
	ID            string     `json:"id" db:"id"`
	Email         string     `json:"email" db:"email"`
	Username      string     `json:"username" db:"username"`
	Phone         *string    `json:"phone,omitempty" db:"phone"`
	PasswordHash  string     `json:"-" db:"password_hash"`
	DisplayName   string     `json:"display_name" db:"display_name"`
	AvatarURL     string     `json:"avatar_url" db:"avatar_url"`
	Bio           string     `json:"bio" db:"bio"`
	Role          string     `json:"role" db:"role"`
	EmailVerified bool       `json:"email_verified" db:"email_verified"`
	PreferredLanguage string `json:"preferred_language" db:"preferred_language"`
	PreferredCalendar string `json:"preferred_calendar" db:"preferred_calendar"`
	CreatedAt     time.Time  `json:"created_at" db:"created_at"`
	UpdatedAt     time.Time  `json:"updated_at" db:"updated_at"`
	DeletedAt     *time.Time `json:"deleted_at,omitempty" db:"deleted_at"`

	IsVerified bool `json:"is_verified" db:"-"`
}

type RefreshToken struct {
	ID         string    `json:"id" db:"id"`
	UserID     string    `json:"user_id" db:"user_id"`
	TokenHash  string    `json:"-" db:"token_hash"`
	Family     string    `json:"family" db:"family"`
	ExpiresAt  time.Time `json:"expires_at" db:"expires_at"`
	Revoked    bool      `json:"revoked" db:"revoked"`
	DeviceName *string   `json:"device_name,omitempty" db:"device_name"`
	Platform   *string   `json:"platform,omitempty" db:"platform"`
	UserAgent  *string   `json:"user_agent,omitempty" db:"user_agent"`
	IP         *string   `json:"ip,omitempty" db:"ip"`
	LastSeenAt time.Time `json:"last_seen_at" db:"last_seen_at"`
	CreatedAt  time.Time `json:"created_at" db:"created_at"`
}

type DeviceMetadata struct {
	DeviceName *string
	Platform   *string
	UserAgent  *string
	IP         *string
}

type SessionResponse struct {
	ID         string    `json:"id"`
	Family     string    `json:"family"`
	DeviceName *string   `json:"device_name,omitempty"`
	Platform   *string   `json:"platform,omitempty"`
	UserAgent  *string   `json:"user_agent,omitempty"`
	IP         *string   `json:"ip,omitempty"`
	LastSeenAt time.Time `json:"last_seen_at"`
	CreatedAt  time.Time `json:"created_at"`
	IsCurrent  bool      `json:"is_current"`
}

type RegisterRequest struct {
	Email    string `json:"email"`
	Username string `json:"username"`
	Password string `json:"password"`
}

type LoginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type AuthResponse struct {
	AccessToken  string `json:"access_token"`
	RefreshToken string `json:"refresh_token"`
	ExpiresIn    int    `json:"expires_in"`
	User         *User  `json:"user"`
}

type RefreshRequest struct {
	RefreshToken string `json:"refresh_token"`
}

type UpdateProfileRequest struct {
	DisplayName       string `json:"display_name"`
	Bio               string `json:"bio"`
	AvatarURL         string `json:"avatar_url"`
	PreferredLanguage string `json:"preferred_language"`
	PreferredCalendar string `json:"preferred_calendar"`
}

type RequestOTPRequest struct {
	Phone   string `json:"phone"`
	Purpose string `json:"purpose"`
}

type VerifyOTPLoginRequest struct {
	Phone string `json:"phone"`
	Code  string `json:"code"`
}

type VerifyOTPRegisterRequest struct {
	Phone    string `json:"phone"`
	Code     string `json:"code"`
	Email    string `json:"email"`
	Username string `json:"username"`
}

type SendSMSRequest struct {
	Recipients []string          `json:"recipients"`
	Pattern    string            `json:"pattern"`
	Params     map[string]string `json:"params"`
	Message    string            `json:"message"`
}
