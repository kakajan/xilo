package handler

import (
	"context"
	"errors"
	"log/slog"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/xilo-platform/xilo/internal/auth/model"
	"github.com/xilo-platform/xilo/internal/auth/repository"
	"github.com/xilo-platform/xilo/internal/auth/service"
)

type AuthHandler struct {
	svc *service.AuthService
}

func NewAuthHandler(svc *service.AuthService) *AuthHandler {
	return &AuthHandler{svc: svc}
}

const (
	cookieAccess  = "xilo_access_token"
	cookieRefresh = "xilo_refresh_token"
)

// @Summary      Register a new user
// @Description  Register with email, username, and password
// @Tags         auth
// @Accept       json
// @Produce      json
// @Param        request body model.RegisterRequest true "Registration data"
// @Success      201  {object}  model.AuthResponse
// @Failure      400  {object}  map[string]string
// @Router       /auth/register [post]
func (h *AuthHandler) Register(c *fiber.Ctx) error {
	var req model.RegisterRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid request body"})
	}

	resp, err := h.svc.Register(authContextWithDevice(c), &req)
	if err != nil {
		slog.Warn("register failed", "error", err)
		return writeAuthError(c, fiber.StatusBadRequest, err)
	}

	setAuthCookies(c, resp.AccessToken, resp.RefreshToken, resp.ExpiresIn)
	return c.Status(fiber.StatusCreated).JSON(resp)
}

// @Summary      Login with email and password
// @Description  Login with email and password
// @Tags         auth
// @Accept       json
// @Produce      json
// @Param        request body model.LoginRequest true "Login credentials"
// @Success      200  {object}  model.AuthResponse
// @Failure      400  {object}  map[string]string
// @Failure      401  {object}  map[string]string
// @Router       /auth/login [post]
func (h *AuthHandler) Login(c *fiber.Ctx) error {
	var req model.LoginRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid request body"})
	}

	resp, err := h.svc.Login(authContextWithDevice(c), &req)
	if err != nil {
		if errors.Is(err, service.ErrInvalidCredentials) {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "invalid email or password"})
		}
		slog.Error("login failed", "error", err)
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "internal server error"})
	}

	setAuthCookies(c, resp.AccessToken, resp.RefreshToken, resp.ExpiresIn)
	return c.JSON(resp)
}

// @Summary      Refresh access token
// @Description  Refresh access token using refresh token
// @Tags         auth
// @Accept       json
// @Produce      json
// @Param        request body model.RefreshRequest true "Refresh token"
// @Success      200  {object}  model.AuthResponse
// @Failure      400  {object}  map[string]string
// @Failure      401  {object}  map[string]string
// @Router       /auth/refresh [post]
func (h *AuthHandler) Refresh(c *fiber.Ctx) error {
	var req model.RefreshRequest
	if err := c.BodyParser(&req); err != nil {
		refreshToken := c.Cookies(cookieRefresh)
		if refreshToken != "" {
			req.RefreshToken = refreshToken
		} else {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid request body"})
		}
	}

	resp, err := h.svc.Refresh(authContextWithDevice(c), req.RefreshToken)
	if err != nil {
		if errors.Is(err, service.ErrTokenReused) {
			clearAuthCookies(c)
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "token reuse detected — all sessions invalidated"})
		}
		clearAuthCookies(c)
		return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "invalid refresh token"})
	}

	setAuthCookies(c, resp.AccessToken, resp.RefreshToken, resp.ExpiresIn)
	return c.JSON(resp)
}

// @Summary      Logout current user
// @Description  Logout current user and invalidate refresh token
// @Tags         auth
// @Accept       json
// @Produce      json
// @Param        request body model.RefreshRequest false "Refresh token (optional)"
// @Security     BearerAuth
// @Success      200  {object}  map[string]string
// @Router       /auth/logout [post]
func (h *AuthHandler) Logout(c *fiber.Ctx) error {
	var req model.RefreshRequest
	if err := c.BodyParser(&req); err != nil {
		req.RefreshToken = c.Cookies(cookieRefresh)
	}

	if req.RefreshToken != "" {
		if err := h.svc.Logout(c.UserContext(), req.RefreshToken); err != nil {
			slog.Warn("logout failed", "error", err)
		}
	}

	clearAuthCookies(c)
	return c.Status(fiber.StatusOK).JSON(fiber.Map{"message": "logged out successfully"})
}

// @Summary      Get current user profile
// @Description  Get the profile of the currently authenticated user
// @Tags         auth
// @Produce      json
// @Security     BearerAuth
// @Success      200  {object}  model.User
// @Failure      401  {object}  map[string]string
// @Router       /auth/me [get]
func (h *AuthHandler) Me(c *fiber.Ctx) error {
	userID, ok := c.Locals("userID").(string)
	if !ok {
		return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "unauthorized"})
	}
	user, err := h.svc.Me(c.UserContext(), userID)
	if err != nil {
		slog.Error("me failed", "error", err)
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "internal server error"})
	}
	return c.JSON(user)
}

// @Summary      Update current user profile
// @Description  Update the profile of the currently authenticated user
// @Tags         auth
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        request body model.UpdateProfileRequest true "Profile data"
// @Success      200  {object}  model.User
// @Failure      400  {object}  map[string]string
// @Failure      401  {object}  map[string]string
// @Router       /auth/me [patch]
func (h *AuthHandler) UpdateProfile(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	var req model.UpdateProfileRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid request body"})
	}

	user, err := h.svc.UpdateProfile(c.UserContext(), userID, &req)
	if err != nil {
		if errors.Is(err, repository.ErrUsernameExists) {
			return writeAuthError(c, fiber.StatusConflict, err)
		}
		slog.Warn("update profile failed", "error", err)
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": err.Error()})
	}
	return c.JSON(user)
}

// @Summary      Request OTP code
// @Description  Request a one-time password for authentication
// @Tags         auth
// @Accept       json
// @Produce      json
// @Param        request body model.RequestOTPRequest true "OTP request"
// @Success      200  {object}  map[string]string
// @Failure      400  {object}  map[string]string
// @Failure      429  {object}  map[string]string
// @Router       /auth/otp/request [post]
func (h *AuthHandler) RequestOTP(c *fiber.Ctx) error {
	var req model.RequestOTPRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid request body"})
	}

	if err := h.svc.RequestOTP(c.UserContext(), &req); err != nil {
		if errors.Is(err, service.ErrOTPRateLimited) {
			return c.Status(fiber.StatusTooManyRequests).JSON(fiber.Map{"error": "otp already sent, please wait"})
		}
		slog.Warn("request otp failed", "error", err)
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "failed to send otp"})
	}

	return c.JSON(fiber.Map{"message": "otp sent successfully"})
}

// @Summary      Verify OTP for login
// @Description  Verify OTP code for login
// @Tags         auth
// @Accept       json
// @Produce      json
// @Param        request body model.VerifyOTPLoginRequest true "OTP verification"
// @Success      200  {object}  model.AuthResponse
// @Failure      400  {object}  map[string]string
// @Router       /auth/otp/verify-login [post]
func (h *AuthHandler) VerifyOTPLogin(c *fiber.Ctx) error {
	var req model.VerifyOTPLoginRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid request body"})
	}

	resp, err := h.svc.VerifyOTPLogin(authContextWithDevice(c), &req)
	if err != nil {
		if errors.Is(err, service.ErrInvalidOTP) {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid or expired otp"})
		}
		slog.Warn("verify otp login failed", "error", err)
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "verification failed"})
	}

	setAuthCookies(c, resp.AccessToken, resp.RefreshToken, resp.ExpiresIn)
	return c.JSON(resp)
}

// @Summary      Verify OTP for registration
// @Description  Verify OTP code for registration
// @Tags         auth
// @Accept       json
// @Produce      json
// @Param        request body model.VerifyOTPRegisterRequest true "OTP registration verification"
// @Success      201  {object}  model.AuthResponse
// @Failure      400  {object}  map[string]string
// @Router       /auth/otp/verify-register [post]
func (h *AuthHandler) VerifyOTPRegister(c *fiber.Ctx) error {
	var req model.VerifyOTPRegisterRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid request body"})
	}

	resp, err := h.svc.VerifyOTPRegister(authContextWithDevice(c), &req)
	if err != nil {
		if errors.Is(err, service.ErrInvalidOTP) {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "invalid or expired otp"})
		}
		slog.Warn("verify otp register failed", "error", err)
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "verification failed"})
	}

	setAuthCookies(c, resp.AccessToken, resp.RefreshToken, resp.ExpiresIn)
	return c.Status(fiber.StatusCreated).JSON(resp)
}

// @Summary      List active auth sessions
// @Description  List non-revoked, non-expired refresh-token sessions for the current user
// @Tags         auth
// @Produce      json
// @Security     BearerAuth
// @Param        X-Refresh-Token header string false "Current refresh token to mark is_current"
// @Param        refresh_token query string false "Current refresh token to mark is_current"
// @Success      200  {array}  model.SessionResponse
// @Failure      401  {object}  map[string]string
// @Router       /auth/sessions [get]
func (h *AuthHandler) ListSessions(c *fiber.Ctx) error {
	userID, ok := c.Locals("userID").(string)
	if !ok {
		return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "unauthorized"})
	}

	currentRefresh := strings.TrimSpace(c.Get("X-Refresh-Token"))
	if currentRefresh == "" {
		currentRefresh = strings.TrimSpace(c.Query("refresh_token"))
	}
	if currentRefresh == "" {
		currentRefresh = c.Cookies(cookieRefresh)
	}

	sessions, err := h.svc.ListSessions(c.UserContext(), userID, currentRefresh)
	if err != nil {
		slog.Error("list sessions failed", "error", err)
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "internal server error"})
	}
	return c.JSON(sessions)
}

// @Summary      Revoke an auth session
// @Description  Revoke all refresh tokens in the session family
// @Tags         auth
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Session ID"
// @Success      200  {object}  map[string]string
// @Failure      401  {object}  map[string]string
// @Failure      404  {object}  map[string]string
// @Router       /auth/sessions/{id} [delete]
func (h *AuthHandler) RevokeSession(c *fiber.Ctx) error {
	userID, ok := c.Locals("userID").(string)
	if !ok {
		return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "unauthorized"})
	}

	if err := h.svc.RevokeSession(c.UserContext(), userID, c.Params("id")); err != nil {
		if errors.Is(err, repository.ErrSessionNotFound) {
			return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "session not found"})
		}
		slog.Error("revoke session failed", "error", err)
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "internal server error"})
	}
	return c.JSON(fiber.Map{"message": "session revoked"})
}

func authContextWithDevice(c *fiber.Ctx) context.Context {
	meta := &model.DeviceMetadata{
		DeviceName: optionalTrimmedString(c.Get("X-Device-Name")),
		Platform:   optionalTrimmedString(c.Get("X-Device-Platform")),
		UserAgent:  optionalTrimmedString(c.Get("User-Agent")),
		IP:         optionalTrimmedString(c.IP()),
	}
	return service.ContextWithDeviceMetadata(c.UserContext(), meta)
}

func optionalTrimmedString(value string) *string {
	value = strings.TrimSpace(value)
	if value == "" {
		return nil
	}
	return &value
}

func setAuthCookies(c *fiber.Ctx, access, refresh string, expiresIn int) {
	// Lax (not Strict): SPA on aile.ir calls API on brain.aile.ir (same-site,
	// cross-origin). Strict refresh cookies are easy to drop on those fetches.
	secure := c.Secure() || strings.EqualFold(c.Get("X-Forwarded-Proto"), "https")
	c.Cookie(&fiber.Cookie{
		Name:     cookieAccess,
		Value:    access,
		Path:     "/",
		Expires:  time.Now().Add(time.Duration(expiresIn) * time.Second),
		HTTPOnly: true,
		Secure:   secure,
		SameSite: "Lax",
	})

	c.Cookie(&fiber.Cookie{
		Name:     cookieRefresh,
		Value:    refresh,
		Path:     "/api/auth/refresh",
		Expires:  time.Now().Add(7 * 24 * time.Hour),
		HTTPOnly: true,
		Secure:   secure,
		SameSite: "Lax",
	})
}

func clearAuthCookies(c *fiber.Ctx) {
	secure := c.Secure() || strings.EqualFold(c.Get("X-Forwarded-Proto"), "https")
	c.Cookie(&fiber.Cookie{
		Name:     cookieAccess,
		Value:    "",
		Path:     "/",
		Expires:  time.Now().Add(-1 * time.Hour),
		HTTPOnly: true,
		Secure:   secure,
		SameSite: "Lax",
	})

	c.Cookie(&fiber.Cookie{
		Name:     cookieRefresh,
		Value:    "",
		Path:     "/api/auth/refresh",
		Expires:  time.Now().Add(-1 * time.Hour),
		HTTPOnly: true,
		Secure:   secure,
		SameSite: "Lax",
	})
}

func writeAuthError(c *fiber.Ctx, status int, err error) error {
	switch {
	case errors.Is(err, repository.ErrEmailExists):
		return c.Status(status).JSON(fiber.Map{"error": "email already exists"})
	case errors.Is(err, repository.ErrUsernameExists):
		return c.Status(status).JSON(fiber.Map{"error": "username already exists"})
	case errors.Is(err, service.ErrInvalidCredentials):
		return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{"error": "invalid email or password"})
	default:
		msg := strings.TrimSpace(err.Error())
		if msg != "" {
			return c.Status(status).JSON(fiber.Map{"error": msg})
		}
		return c.Status(status).JSON(fiber.Map{"error": "registration failed"})
	}
}
