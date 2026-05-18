package s3

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	s3types "github.com/aws/aws-sdk-go-v2/service/s3/types"
	"github.com/xilo-platform/xilo/pkg/storage"
)

type Driver struct {
	client        *s3.Client
	bucket        string
	endpoint      string
	region        string
	useSSL        bool
	usePathStyle  bool
	isMinioCompat bool
}

func New(region, endpoint, accessKey, secretKey, bucket string, useSSL bool) (*Driver, error) {
	usePathStyle := true
	isMinioCompat := false

	if endpoint == "" {
		endpoint = fmt.Sprintf("s3.%s.amazonaws.com", region)
		usePathStyle = false
	} else {
		isMinioCompat = true
	}

	httpClient := &http.Client{
		Timeout: 30 * time.Second,
		Transport: &http.Transport{
			MaxIdleConns:        100,
			MaxIdleConnsPerHost: 20,
			IdleConnTimeout:     90 * time.Second,
		},
	}

	cfg, err := config.LoadDefaultConfig(context.Background(),
		config.WithRegion(region),
		config.WithCredentialsProvider(credentials.NewStaticCredentialsProvider(accessKey, secretKey, "")),
		config.WithHTTPClient(httpClient),
	)
	if err != nil {
		return nil, fmt.Errorf("s3: load config: %w", err)
	}

	client := s3.NewFromConfig(cfg, func(o *s3.Options) {
		o.BaseEndpoint = aws.String(endpoint)
		o.UsePathStyle = usePathStyle
		o.Region = region
	})

	driver := &Driver{
		client:        client,
		bucket:        bucket,
		endpoint:      endpoint,
		region:        region,
		useSSL:        useSSL,
		usePathStyle:  usePathStyle,
		isMinioCompat: isMinioCompat,
	}

	if !isMinioCompat {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		_, err := client.HeadBucket(ctx, &s3.HeadBucketInput{Bucket: aws.String(bucket)})
		if err != nil {
			_, err = client.CreateBucket(ctx, &s3.CreateBucketInput{
				Bucket: aws.String(bucket),
				CreateBucketConfiguration: &s3types.CreateBucketConfiguration{
					LocationConstraint: s3types.BucketLocationConstraint(region),
				},
			})
			if err != nil {
				return nil, fmt.Errorf("s3: create bucket: %w", err)
			}
		}
	}

	return driver, nil
}

func (d *Driver) Upload(ctx context.Context, key string, reader io.Reader, size int64, contentType string) (*storage.UploadResult, error) {
	if contentType == "" {
		contentType = "application/octet-stream"
	}

	input := &s3.PutObjectInput{
		Bucket:        aws.String(d.bucket),
		Key:           aws.String(key),
		Body:          reader,
		ContentType:   aws.String(contentType),
		ContentLength: aws.Int64(size),
	}

	if d.isMinioCompat {
		input.ChecksumAlgorithm = ""
	}

	_, err := d.client.PutObject(ctx, input)
	if err != nil {
		return nil, fmt.Errorf("s3: upload: %w", err)
	}

	return &storage.UploadResult{
		Key:  key,
		URL:  d.GetURL(key),
		Size: size,
	}, nil
}

func (d *Driver) Download(ctx context.Context, key string) (io.ReadCloser, error) {
	resp, err := d.client.GetObject(ctx, &s3.GetObjectInput{
		Bucket: aws.String(d.bucket),
		Key:    aws.String(key),
	})
	if err != nil {
		return nil, fmt.Errorf("s3: download: %w", err)
	}
	return resp.Body, nil
}

func (d *Driver) Delete(ctx context.Context, key string) error {
	_, err := d.client.DeleteObject(ctx, &s3.DeleteObjectInput{
		Bucket: aws.String(d.bucket),
		Key:    aws.String(key),
	})
	if err != nil {
		return fmt.Errorf("s3: delete: %w", err)
	}
	return nil
}

func (d *Driver) GetURL(key string) string {
	protocol := "http"
	if d.useSSL {
		protocol = "https"
	}
	if d.usePathStyle {
		return fmt.Sprintf("%s://%s/%s/%s", protocol, d.endpoint, d.bucket, key)
	}
	return fmt.Sprintf("%s://%s.%s/%s", protocol, d.bucket, d.endpoint, key)
}

func (d *Driver) Bucket() string {
	return d.bucket
}

var _ storage.Driver = (*Driver)(nil)
