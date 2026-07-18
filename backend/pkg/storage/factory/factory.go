package factory

import (
	"fmt"
	"os"

	"github.com/xilo-platform/xilo/pkg/storage"
	"github.com/xilo-platform/xilo/pkg/storage/minio"
	"github.com/xilo-platform/xilo/pkg/storage/s3"
)

func New() (storage.Driver, error) {
	driver := os.Getenv("STORAGE_DRIVER")
	if driver == "" {
		driver = "minio"
	}

	endpoint := os.Getenv("STORAGE_ENDPOINT")
	if endpoint == "" {
		endpoint = "localhost:9000"
	}
	accessKey := os.Getenv("STORAGE_ACCESS_KEY")
	if accessKey == "" {
		accessKey = "minioadmin"
	}
	secretKey := os.Getenv("STORAGE_SECRET_KEY")
	if secretKey == "" {
		secretKey = "minioadmin"
	}
	bucket := os.Getenv("STORAGE_BUCKET")
	if bucket == "" {
		bucket = "xilo-media"
	}
	useSSL := os.Getenv("STORAGE_USE_SSL") == "true"
	publicEndpoint := os.Getenv("STORAGE_PUBLIC_ENDPOINT")
	// Browser-facing URLs: default to HTTPS when public host differs from the
	// internal docker endpoint (minio:9000), even if STORAGE_USE_SSL=false.
	publicUseSSL := useSSL
	if v := os.Getenv("STORAGE_PUBLIC_USE_SSL"); v != "" {
		publicUseSSL = v == "true"
	} else if publicEndpoint != "" && publicEndpoint != endpoint {
		publicUseSSL = true
	}

	switch driver {
	case "minio":
		return minio.New(endpoint, accessKey, secretKey, bucket, useSSL, publicEndpoint, publicUseSSL)
	case "s3":
		region := os.Getenv("STORAGE_REGION")
		if region == "" {
			region = "us-east-1"
		}
		return s3.New(region, endpoint, accessKey, secretKey, bucket, useSSL)
	default:
		return nil, fmt.Errorf("unknown storage driver: %s (valid: minio, s3)", driver)
	}
}
