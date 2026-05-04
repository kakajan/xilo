# Spec: Authentication & Authorization

## Overview
Secure, stateless authentication with JWT access tokens and refresh tokens. Supports email/password and optional OAuth2 providers. All passwords hashed with Argon2id.

---

## Requirements

### REQ-AUTH-001: User Registration

**Given** a visitor without an account  
**When** they submit a valid email, username, and password  
**Then** an account is created, a verification email is sent, and they receive a JWT access + refresh token pair.

**Validation:**
- Email: valid format, unique, max 254 chars
- Username: 3–32 chars, alphanumeric + underscore, unique
- Password: min 8 chars, at least 1 uppercase, 1 number, 1 special char

### REQ-AUTH-002: User Login

**Given** a registered user with verified email  
**When** they submit correct credentials  
**Then** they receive a JWT access token (15min TTL) and refresh token (7 day TTL).

**Edge cases:**
- Account locked after 5 failed attempts (15min cooldown)
- Unverified email: allow login but restrict write operations

### REQ-AUTH-003: Token Refresh

**Given** a valid refresh token  
**When** the access token expires  
**Then** a new access token is issued without re-authentication.

**Constraints:**
- Refresh token rotation: old token invalidated on use
- Refresh token reuse detection: revoke all tokens for user if reused

### REQ-AUTH-004: Password Reset

**Given** a user who forgot their password  
**When** they request a reset via email  
**Then** a time-limited reset token (15min) is sent, allowing password change.

### REQ-AUTH-005: Role-Based Access Control

**Roles:** `reader`, `author`, `editor`, `admin`, `superadmin`

| Role | Permissions |
|------|------------|
| reader | Read posts, write comments, react |
| author | Create/edit own posts, upload media |
| editor | Edit any post, moderate comments |
| admin | Manage users, configure system |
| superadmin | Full system access, billing |

### REQ-AUTH-006: Rate Limiting

**Given** any auth endpoint  
**When** requests exceed threshold  
**Then** return `429 Too Many Requests` with `Retry-After` header.

- Login: 5 req/min per IP
- Registration: 3 req/hour per IP
- Password reset: 3 req/hour per email

### REQ-AUTH-007: OAuth2 (Optional)

Support sign-in via Google and GitHub OAuth2 providers with account linking to existing email.

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | None | Register new user |
| POST | `/api/auth/login` | None | Login |
| POST | `/api/auth/refresh` | Refresh | Refresh access token |
| POST | `/api/auth/logout` | Access | Invalidate refresh token |
| POST | `/api/auth/forgot-password` | None | Request password reset |
| POST | `/api/auth/reset-password` | Reset | Reset password |
| GET | `/api/auth/me` | Access | Get current user profile |
| PATCH | `/api/auth/me` | Access | Update profile |
| POST | `/api/auth/verify-email` | None | Verify email with token |
