package contacthash

import (
	"log/slog"
	"os"
	"strings"
)

const DevPepper = "dev-contact-pepper"

// ResolvePepper returns CONTACT_MATCH_PEPPER from the environment.
// In production (APP_ENV/GO_ENV/ENV = production|prod) an empty value fails closed ("").
// Otherwise the insecure DevPepper default is used with a warning (mirrors localhost DB defaults).
func ResolvePepper() string {
	if v := strings.TrimSpace(os.Getenv("CONTACT_MATCH_PEPPER")); v != "" {
		return v
	}
	if isProductionEnv() {
		return ""
	}
	slog.Warn("CONTACT_MATCH_PEPPER not set; using insecure dev default")
	return DevPepper
}

func isProductionEnv() bool {
	for _, key := range []string{"APP_ENV", "GO_ENV", "ENV"} {
		v := strings.ToLower(strings.TrimSpace(os.Getenv(key)))
		if v == "production" || v == "prod" {
			return true
		}
	}
	return false
}
