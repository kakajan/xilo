package sms

import "context"

type Driver interface {
	SendPattern(ctx context.Context, to string, patternCode string, params map[string]string) (*SendResult, error)
	Send(ctx context.Context, to string, message string) error
}

type SendResult struct {
	MessageID int64  `json:"message_id"`
	Status    bool   `json:"status"`
	Message   string `json:"message"`
}
