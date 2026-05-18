package factory

import (
	"fmt"
	"os"

	"github.com/xilo-platform/xilo/pkg/sms"
	"github.com/xilo-platform/xilo/pkg/sms/ippanel"
)

func New() (sms.Driver, error) {
	driver := os.Getenv("SMS_DRIVER")
	if driver == "" {
		driver = "ippanel"
	}

	apiKey := os.Getenv("SMS_IPPANEL_API_KEY")
	if apiKey == "" {
		return nil, fmt.Errorf("SMS_IPPANEL_API_KEY is required")
	}

	fromNumber := os.Getenv("SMS_FROM_NUMBER")
	if fromNumber == "" {
		return nil, fmt.Errorf("SMS_FROM_NUMBER is required (e.g. +983000505)")
	}

	switch driver {
	case "ippanel":
		return ippanel.NewDriver(apiKey, ippanel.WithFromNumber(fromNumber)), nil
	default:
		return nil, fmt.Errorf("unknown sms driver: %s (valid: ippanel)", driver)
	}
}
