package model

import (
	"time"
)

type Media struct {
	ID           string            `json:"id" db:"id"`
	UserID       string            `json:"user_id" db:"user_id"`
	Filename     string            `json:"filename" db:"filename"`
	OriginalName string            `json:"original_name" db:"original_name"`
	MimeType     string            `json:"mime_type" db:"mime_type"`
	SizeBytes    int64             `json:"size_bytes" db:"size_bytes"`
	Width        int               `json:"width" db:"width"`
	Height       int               `json:"height" db:"height"`
	Variants     map[string]string `json:"variants" db:"variants"`
	CreatedAt    time.Time         `json:"created_at" db:"created_at"`
}

type UploadResponse struct {
	ID       string            `json:"id"`
	URL      string            `json:"url"`
	Variants map[string]string `json:"variants"`
	Width    int               `json:"width"`
	Height   int               `json:"height"`
	Size     int64             `json:"size"`
}
