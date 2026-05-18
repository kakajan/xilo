package storage

import (
	"context"
	"io"
)

type Driver interface {
	Upload(ctx context.Context, key string, reader io.Reader, size int64, contentType string) (*UploadResult, error)
	Download(ctx context.Context, key string) (io.ReadCloser, error)
	Delete(ctx context.Context, key string) error
	GetURL(key string) string
	Bucket() string
}

type UploadResult struct {
	Key  string
	URL  string
	Size int64
}
