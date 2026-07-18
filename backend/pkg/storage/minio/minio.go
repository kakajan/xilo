package minio

import (
	"context"
	"fmt"
	"io"

	"github.com/minio/minio-go/v7"
	minioCreds "github.com/minio/minio-go/v7/pkg/credentials"
	"github.com/xilo-platform/xilo/pkg/storage"
)

type Driver struct {
	client         *minio.Client
	bucket         string
	endpoint       string
	publicEndpoint string
	useSSL         bool
	publicUseSSL   bool
}

// New creates a MinIO driver.
// useSSL is for the internal client connection (usually false for docker minio:9000).
// publicUseSSL controls the scheme of browser-facing GetURL links.
func New(endpoint, accessKey, secretKey, bucket string, useSSL bool, publicEndpoint string, publicUseSSL bool) (*Driver, error) {
	client, err := minio.New(endpoint, &minio.Options{
		Creds:        minioCreds.NewStaticV4(accessKey, secretKey, ""),
		Secure:       useSSL,
		BucketLookup: minio.BucketLookupPath,
	})
	if err != nil {
		return nil, fmt.Errorf("minio: create client: %w", err)
	}

	ctx := context.Background()
	exists, err := client.BucketExists(ctx, bucket)
	if err != nil {
		return nil, fmt.Errorf("minio: check bucket: %w", err)
	}

	if !exists {
		err = client.MakeBucket(ctx, bucket, minio.MakeBucketOptions{})
		if err != nil {
			return nil, fmt.Errorf("minio: create bucket: %w", err)
		}
	}

	if publicEndpoint == "" {
		publicEndpoint = endpoint
	}

	return &Driver{
		client:         client,
		bucket:         bucket,
		endpoint:       endpoint,
		publicEndpoint: publicEndpoint,
		useSSL:         useSSL,
		publicUseSSL:   publicUseSSL,
	}, nil
}

func (d *Driver) Upload(ctx context.Context, key string, reader io.Reader, size int64, contentType string) (*storage.UploadResult, error) {
	if contentType == "" {
		contentType = "application/octet-stream"
	}

	info, err := d.client.PutObject(ctx, d.bucket, key, reader, size, minio.PutObjectOptions{
		ContentType: contentType,
	})
	if err != nil {
		return nil, fmt.Errorf("minio: upload: %w", err)
	}

	return &storage.UploadResult{
		Key:  info.Key,
		URL:  d.GetURL(key),
		Size: info.Size,
	}, nil
}

func (d *Driver) Download(ctx context.Context, key string) (io.ReadCloser, error) {
	obj, err := d.client.GetObject(ctx, d.bucket, key, minio.GetObjectOptions{})
	if err != nil {
		return nil, fmt.Errorf("minio: download: %w", err)
	}
	return obj, nil
}

func (d *Driver) Delete(ctx context.Context, key string) error {
	err := d.client.RemoveObject(ctx, d.bucket, key, minio.RemoveObjectOptions{})
	if err != nil {
		return fmt.Errorf("minio: delete: %w", err)
	}
	return nil
}

func (d *Driver) GetURL(key string) string {
	protocol := "http"
	if d.publicUseSSL {
		protocol = "https"
	}
	return fmt.Sprintf("%s://%s/%s/%s", protocol, d.publicEndpoint, d.bucket, key)
}

func (d *Driver) Bucket() string {
	return d.bucket
}

var _ storage.Driver = (*Driver)(nil)
