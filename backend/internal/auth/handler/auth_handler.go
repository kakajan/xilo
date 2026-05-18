package handler

import (
	"errors"
	"log/slog"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/xilo-platform/xilo/internal/auth/model"
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

	resp, err := h.svc.Register(c.UserContext(), &req)
	if err != nil {
		slog.Warn("register failed", "error", err)
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "registration failed"})
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

	resp, err := h.svc.Login(c.UserContext(), &req)
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

	resp, err := h.svc.Refresh(c.UserContext(), req.RefreshToken)
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
		slog.Warn("update profile failed", "error", err)
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{"error": "update failed"})
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

	resp, err := h.svc.VerifyOTPLogin(c.UserContext(), &req)
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

	resp, err := h.svc.VerifyOTPRegister(c.UserContext(), &req)
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

func setAuthCookies(c *fiber.Ctx, access, refresh string, expiresIn int) {
	c.Cookie(&fiber.Cookie{
		Name:     cookieAccess,
		Value:    access,
		Path:     "/",
		Expires:  time.Now().Add(time.Duration(expiresIn) * time.Second),
		HTTPOnly: true,
		Secure:   c.Secure(),
		SameSite: "Lax",
	})

	c.Cookie(&fiber.Cookie{
		Name:     cookieRefresh,
		Value:    refresh,
		Path:     "/api/auth/refresh",
		Expires:  time.Now().Add(7 * 24 * time.Hour),
		HTTPOnly: true,
		Secure:   c.Secure(),
		SameSite: "Strict",
	})
}

func clearAuthCookies(c *fiber.Ctx) {
	c.Cookie(&fiber.Cookie{
		Name:     cookieAccess,
		Value:    "",
		Path:     "/",
		Expires:  time.Now().Add(-1 * time.Hour),
		HTTPOnly: true,
		Secure:   c.Secure(),
		SameSite: "Lax",
	})

	c.Cookie(&fiber.Cookie{
		Name:     cookieRefresh,
		Value:    "",
		Path:     "/api/auth/refresh",
		Expires:  time.Now().Add(-1 * time.Hour),
		HTTPOnly: true,
		Secure:   c.Secure(),
		SameSite: "Strict",
	})
}
