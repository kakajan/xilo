package util

// IsVerifiedWriter returns true when the user should display a verified badge.
func IsVerifiedWriter(role string) bool {
	switch role {
	case "writer", "admin", "editor", "author", "superadmin":
		return true
	default:
		return false
	}
}
