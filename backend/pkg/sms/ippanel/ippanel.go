package ippanel

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/xilo-platform/xilo/pkg/sms"
)

const baseURL = "https://edge.ippanel.com/v1/api/send"

type Driver struct {
	apiKey     string
	fromNumber string
	client     *http.Client
}

type sendRequest struct {
	SendingType string            `json:"sending_type"`
	FromNumber  string            `json:"from_number"`
	Code        string            `json:"code"`
	Recipients  []string          `json:"recipients"`
	Params      map[string]string `json:"params"`
}

type sendResponse struct {
	Data *struct {
		MessageOutboxIDs []int64 `json:"message_outbox_ids"`
	} `json:"data"`
	Meta struct {
		Status           bool     `json:"status"`
		Message          string   `json:"message"`
		MessageParams    []string `json:"message_parameters"`
		MessageCode      string   `json:"message_code"`
		Errors           map[string][]string `json:"errors"`
	} `json:"meta"`
}

type DriverOption func(*Driver)

func WithHTTPClient(c *http.Client) DriverOption {
	return func(d *Driver) {
		d.client = c
	}
}

func WithFromNumber(n string) DriverOption {
	return func(d *Driver) {
		d.fromNumber = n
	}
}

func NewDriver(apiKey string, opts ...DriverOption) *Driver {
	d := &Driver{
		apiKey: apiKey,
		client: &http.Client{Timeout: 10 * time.Second},
	}
	for _, opt := range opts {
		opt(d)
	}
	return d
}

func (d *Driver) SendPattern(ctx context.Context, to string, patternCode string, params map[string]string) (*sms.SendResult, error) {
	req := sendRequest{
		SendingType: "pattern",
		FromNumber:  d.fromNumber,
		Code:        patternCode,
		Recipients:  []string{to},
		Params:      params,
	}

	body, err := json.Marshal(req)
	if err != nil {
		return nil, fmt.Errorf("marshal request: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, baseURL, bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Authorization", d.apiKey)

	resp, err := d.client.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("send pattern sms: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response: %w", err)
	}

	var sr sendResponse
	if err := json.Unmarshal(respBody, &sr); err != nil {
		return nil, fmt.Errorf("unmarshal response: %w", err)
	}

	if resp.StatusCode != http.StatusOK || !sr.Meta.Status {
		errMsg := sr.Meta.Message
		if errMsg == "" {
			errMsg = fmt.Sprintf("http status %d", resp.StatusCode)
		}
		return &sms.SendResult{
			Status:  false,
			Message: errMsg,
		}, nil
	}

	var messageID int64
	if sr.Data != nil && len(sr.Data.MessageOutboxIDs) > 0 {
		messageID = sr.Data.MessageOutboxIDs[0]
	}

	return &sms.SendResult{
		MessageID: messageID,
		Status:    true,
		Message:   sr.Meta.Message,
	}, nil
}

func (d *Driver) Send(ctx context.Context, to string, message string) error {
	req := sendRequest{
		SendingType: "pattern",
		FromNumber:  d.fromNumber,
		Code:        "",
		Recipients:  []string{to},
		Params:      map[string]string{"message": message},
	}

	body, err := json.Marshal(req)
	if err != nil {
		return fmt.Errorf("marshal request: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, baseURL, bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("create request: %w", err)
	}

	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Authorization", d.apiKey)

	resp, err := d.client.Do(httpReq)
	if err != nil {
		return fmt.Errorf("send sms: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("read response: %w", err)
	}

	var sr sendResponse
	if err := json.Unmarshal(respBody, &sr); err != nil {
		return fmt.Errorf("unmarshal response: %w", err)
	}

	if resp.StatusCode != http.StatusOK || !sr.Meta.Status {
		errMsg := sr.Meta.Message
		if errMsg == "" {
			errMsg = fmt.Sprintf("http status %d", resp.StatusCode)
		}
		return fmt.Errorf("send sms failed: %s", errMsg)
	}

	return nil
}
