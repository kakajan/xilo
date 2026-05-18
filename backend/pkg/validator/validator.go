package validator

import (
	"regexp"
	"strings"
	"unicode"
)

var (
	emailRegex    = regexp.MustCompile(`^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$`)
	usernameRegex = regexp.MustCompile(`^[a-zA-Z0-9_]{3,32}$`)
	tagRegex      = regexp.MustCompile(`^[a-zA-Z0-9_\-\p{Arabic}]{1,30}$`)
	slugRegex     = regexp.MustCompile(`^[a-z0-9]+(?:-[a-z0-9]+)*$`)
)

type ValidationError struct {
	Field   string `json:"field"`
	Message string `json:"message"`
}

func ValidateEmail(email string) *ValidationError {
	email = strings.TrimSpace(email)
	if email == "" {
		return &ValidationError{"email", "email is required"}
	}
	if len(email) > 254 {
		return &ValidationError{"email", "email must be at most 254 characters"}
	}
	if !emailRegex.MatchString(email) {
		return &ValidationError{"email", "invalid email format"}
	}
	return nil
}

func ValidateUsername(username string) *ValidationError {
	username = strings.TrimSpace(username)
	if username == "" {
		return &ValidationError{"username", "username is required"}
	}
	if len(username) < 3 || len(username) > 32 {
		return &ValidationError{"username", "username must be between 3 and 32 characters"}
	}
	if !usernameRegex.MatchString(username) {
		return &ValidationError{"username", "username must contain only alphanumeric characters and underscores"}
	}
	return nil
}

func ValidatePassword(password string) []*ValidationError {
	var errors []*ValidationError

	if len(password) < 8 {
		errors = append(errors, &ValidationError{"password", "password must be at least 8 characters"})
	}

	var hasUpper, hasNumber, hasSpecial bool
	for _, c := range password {
		switch {
		case unicode.IsUpper(c):
			hasUpper = true
		case unicode.IsNumber(c):
			hasNumber = true
		case unicode.IsPunct(c) || unicode.IsSymbol(c):
			hasSpecial = true
		}
	}

	if !hasUpper {
		errors = append(errors, &ValidationError{"password", "password must contain at least one uppercase letter"})
	}
	if !hasNumber {
		errors = append(errors, &ValidationError{"password", "password must contain at least one number"})
	}
	if !hasSpecial {
		errors = append(errors, &ValidationError{"password", "password must contain at least one special character"})
	}

	if len(errors) > 0 {
		return errors
	}
	return nil
}

func ValidateTitle(title string) *ValidationError {
	title = strings.TrimSpace(title)
	if title == "" {
		return &ValidationError{"title", "title is required"}
	}
	if len(title) > 200 {
		return &ValidationError{"title", "title must be at most 200 characters"}
	}
	return nil
}

func ValidateSlug(slug string) *ValidationError {
	slug = strings.TrimSpace(slug)
	if slug == "" {
		return &ValidationError{"slug", "slug is required"}
	}
	if len(slug) > 250 {
		return &ValidationError{"slug", "slug must be at most 250 characters"}
	}
	if !slugRegex.MatchString(slug) {
		return &ValidationError{"slug", "slug must contain only lowercase letters, numbers, and hyphens"}
	}
	return nil
}

func ValidateTags(tags []string) []*ValidationError {
	var errors []*ValidationError
	if len(tags) > 10 {
		errors = append(errors, &ValidationError{"tags", "maximum 10 tags allowed"})
	}
	for i, tag := range tags {
		tag = strings.TrimSpace(tag)
		if len(tag) < 1 || len(tag) > 30 {
			errors = append(errors, &ValidationError{Field: "tags", Message: "each tag must be between 1 and 30 characters"})
			break
		}
		if !tagRegex.MatchString(tag) {
			errors = append(errors, &ValidationError{Field: "tags", Message: "tag contains invalid characters"})
			break
		}
		_ = i
	}
	return errors
}

func ValidateComment(text string) *ValidationError {
	text = strings.TrimSpace(text)
	if text == "" {
		return &ValidationError{"text", "comment text is required"}
	}
	if len(text) > 5000 {
		return &ValidationError{"text", "comment must be at most 5000 characters"}
	}
	return nil
}
