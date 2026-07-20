package service

import (
	"bytes"
	"context"
	"fmt"
	"image"
	_ "image/gif"
	_ "image/jpeg"
	"image/png"
	"io"
	"path/filepath"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/xilo-platform/xilo/internal/media/model"
	"github.com/xilo-platform/xilo/internal/media/repository"
	"github.com/xilo-platform/xilo/pkg/storage"

	"golang.org/x/image/draw"
	_ "golang.org/x/image/webp"
)

type MediaService struct {
	repo    *repository.MediaRepo
	storage storage.Driver
}

func NewMediaService(repo *repository.MediaRepo, storageDriver storage.Driver) *MediaService {
	return &MediaService{repo: repo, storage: storageDriver}
}

var allowedImageMimeTypes = map[string]bool{
	"image/jpeg": true,
	"image/png":  true,
	"image/webp": true,
	"image/gif":  true,
}

var allowedAudioMimeTypes = map[string]bool{
	"audio/mpeg": true,
	"audio/mp4":  true,
	"audio/aac":  true,
	"audio/ogg":  true,
	"audio/wav":  true,
	"audio/webm": true,
	"audio/x-wav": true,
	"audio/wave":  true,
}

var maxImageFileSize int64 = 10 * 1024 * 1024
var maxAudioFileSize int64 = 50 * 1024 * 1024
var maxImageDimension = 5000

func isAllowedUploadMime(mimeType string) bool {
	return allowedImageMimeTypes[mimeType] || allowedAudioMimeTypes[mimeType]
}

func maxUploadSize(mimeType string) int64 {
	if allowedAudioMimeTypes[mimeType] {
		return maxAudioFileSize
	}
	return maxImageFileSize
}

func (s *MediaService) Upload(ctx context.Context, userID string, filename string, reader io.Reader, size int64, mimeType string) (*model.UploadResponse, error) {
	mimeType = normalizeMimeType(mimeType, filename)
	if !isAllowedUploadMime(mimeType) {
		return nil, fmt.Errorf("unsupported mime type: %s", mimeType)
	}
	maxSize := maxUploadSize(mimeType)
	if size > maxSize {
		return nil, fmt.Errorf("file too large: %d bytes (max %d)", size, maxSize)
	}

	ext := strings.ToLower(filepath.Ext(filename))
	if allowedAudioMimeTypes[mimeType] {
		if ext == "" {
			ext = audioExtForMime(mimeType)
		}
	} else if ext == ".jpeg" || ext == "" {
		ext = ".jpg"
	}

	mediaID := uuid.New().String()
	baseKey := fmt.Sprintf("%s/%s", userID, mediaID)
	storageKey := fmt.Sprintf("%s/original%s", baseKey, ext)

	result, err := s.storage.Upload(ctx, storageKey, reader, size, mimeType)
	if err != nil {
		return nil, fmt.Errorf("upload file: %w", err)
	}

	media := &model.Media{
		ID:           mediaID,
		UserID:       userID,
		Filename:     storageKey,
		OriginalName: filename,
		MimeType:     mimeType,
		SizeBytes:    result.Size,
		Variants:     model.JSONMap{"original": result.URL},
		CreatedAt:    time.Now(),
	}

	if err := s.repo.Create(ctx, media); err != nil {
		s.storage.Delete(context.Background(), storageKey)
		return nil, fmt.Errorf("save media record: %w", err)
	}

	return &model.UploadResponse{
		ID:       mediaID,
		URL:      result.URL,
		Variants: media.Variants,
		Size:     result.Size,
	}, nil
}

func (s *MediaService) UploadAvatar(ctx context.Context, userID string, filename string, reader io.Reader, size int64, mimeType string) (*model.UploadResponse, error) {
	mimeType = normalizeMimeType(mimeType, filename)
	if !allowedImageMimeTypes[mimeType] {
		return nil, fmt.Errorf("unsupported mime type: %s", mimeType)
	}
	if size > maxImageFileSize {
		return nil, fmt.Errorf("file too large: %d bytes (max %d)", size, maxImageFileSize)
	}

	rawData, err := io.ReadAll(reader)
	if err != nil {
		return nil, fmt.Errorf("read avatar data: %w", err)
	}

	processed, err := processAvatar(rawData)
	if err != nil {
		return nil, fmt.Errorf("process avatar: %w", err)
	}

	mediaID := uuid.New().String()
	storageKey := fmt.Sprintf("%s/%s/avatar.png", userID, mediaID)

	buf := bytes.NewReader(processed)
	result, err := s.storage.Upload(ctx, storageKey, buf, int64(len(processed)), "image/png")
	if err != nil {
		return nil, fmt.Errorf("upload avatar: %w", err)
	}

	media := &model.Media{
		ID:           mediaID,
		UserID:       userID,
		Filename:     storageKey,
		OriginalName: filename,
		MimeType:     "image/png",
		SizeBytes:    result.Size,
		Width:        256,
		Height:       256,
		Variants:     model.JSONMap{"256x256": result.URL},
		CreatedAt:    time.Now(),
	}

	if err := s.repo.Create(ctx, media); err != nil {
		s.storage.Delete(context.Background(), storageKey)
		return nil, fmt.Errorf("save avatar record: %w", err)
	}

	return &model.UploadResponse{
		ID:       mediaID,
		URL:      result.URL,
		Variants: model.JSONMap{"avatar": result.URL},
		Width:    256,
		Height:   256,
		Size:     result.Size,
	}, nil
}

func normalizeMimeType(mimeType, filename string) string {
	mimeType = strings.TrimSpace(strings.ToLower(mimeType))
	if i := strings.Index(mimeType, ";"); i >= 0 {
		mimeType = strings.TrimSpace(mimeType[:i])
	}
	if mimeType != "" && mimeType != "application/octet-stream" {
		return mimeType
	}
	switch strings.ToLower(filepath.Ext(filename)) {
	case ".jpg", ".jpeg":
		return "image/jpeg"
	case ".png":
		return "image/png"
	case ".webp":
		return "image/webp"
	case ".gif":
		return "image/gif"
	case ".mp3":
		return "audio/mpeg"
	case ".m4a", ".mp4":
		return "audio/mp4"
	case ".aac":
		return "audio/aac"
	case ".ogg", ".oga":
		return "audio/ogg"
	case ".wav":
		return "audio/wav"
	case ".webm":
		return "audio/webm"
	default:
		if mimeType == "" {
			return "application/octet-stream"
		}
		return mimeType
	}
}

func audioExtForMime(mimeType string) string {
	switch mimeType {
	case "audio/mpeg":
		return ".mp3"
	case "audio/mp4":
		return ".m4a"
	case "audio/aac":
		return ".aac"
	case "audio/ogg":
		return ".ogg"
	case "audio/wav", "audio/x-wav", "audio/wave":
		return ".wav"
	case "audio/webm":
		return ".webm"
	default:
		return ".bin"
	}
}

func processAvatar(data []byte) ([]byte, error) {
	cfg, _, err := image.DecodeConfig(bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("decode config: %w", err)
	}
	if cfg.Width > maxImageDimension || cfg.Height > maxImageDimension {
		return nil, fmt.Errorf("image too large: %dx%d (max %d)", cfg.Width, cfg.Height, maxImageDimension)
	}

	img, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("decode image: %w", err)
	}

	cropped := cropSquare(img)
	resized := resizeImage(cropped, 256, 256)

	var buf bytes.Buffer
	if err := png.Encode(&buf, resized); err != nil {
		return nil, fmt.Errorf("encode png: %w", err)
	}
	return buf.Bytes(), nil
}

func cropSquare(img image.Image) image.Image {
	bounds := img.Bounds()
	w := bounds.Dx()
	h := bounds.Dy()

	var cropRect image.Rectangle
	if w > h {
		x0 := (w - h) / 2
		cropRect = image.Rect(x0, 0, x0+h, h)
	} else {
		y0 := (h - w) / 2
		cropRect = image.Rect(0, y0, w, y0+w)
	}

	result := image.NewRGBA(image.Rect(0, 0, cropRect.Dx(), cropRect.Dy()))
	draw.Draw(result, result.Bounds(), img, cropRect.Min, draw.Src)
	return result
}

func resizeImage(img image.Image, width, height int) image.Image {
	result := image.NewRGBA(image.Rect(0, 0, width, height))
	draw.ApproxBiLinear.Scale(result, result.Bounds(), img, img.Bounds(), draw.Src, nil)
	return result
}

func (s *MediaService) GetByID(ctx context.Context, id string) (*model.Media, error) {
	return s.repo.GetByID(ctx, id)
}

func (s *MediaService) ListByUser(ctx context.Context, userID string, cursor string, limit int) ([]*model.Media, string, error) {
	return s.repo.ListByUser(ctx, userID, cursor, limit)
}

func (s *MediaService) Delete(ctx context.Context, id string, userID string) error {
	media, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if media.UserID != userID {
		return fmt.Errorf("only the owner can delete this media")
	}
	if err := s.storage.Delete(ctx, media.Filename); err != nil {
		return fmt.Errorf("delete from storage: %w", err)
	}
	for _, variantURL := range media.Variants {
		if variantURL != "" {
			key := strings.TrimPrefix(variantURL, s.storage.GetURL("")+"/")
			s.storage.Delete(ctx, key)
		}
	}
	return s.repo.Delete(ctx, id)
}
