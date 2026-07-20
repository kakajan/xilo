package push

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"os"
	"strings"
	"sync"
	"time"

	"golang.org/x/oauth2"
	"golang.org/x/oauth2/google"
)

// Sender delivers FCM HTTP v1 messages when FIREBASE_CREDENTIALS (+ project) are set.
// Without credentials it is a no-op (in-app notifications still work).
type Sender struct {
	projectID string
	client    *http.Client
	mu        sync.Mutex
	tokenSrc  oauth2.TokenSource
	enabled   bool
}

func NewSenderFromEnv() *Sender {
	creds := strings.TrimSpace(os.Getenv("FIREBASE_CREDENTIALS"))
	projectID := strings.TrimSpace(os.Getenv("FIREBASE_PROJECT_ID"))
	if creds == "" {
		slog.Info("FCM push disabled: FIREBASE_CREDENTIALS not set")
		return &Sender{enabled: false}
	}

	var data []byte
	if strings.HasPrefix(strings.TrimSpace(creds), "{") {
		data = []byte(creds)
	} else {
		raw, readErr := os.ReadFile(creds)
		if readErr != nil {
			slog.Info("FCM push disabled: credentials file missing", "path", creds)
			return &Sender{enabled: false}
		}
		data = raw
	}

	ctx := context.Background()
	conf, err := google.JWTConfigFromJSON(data, "https://www.googleapis.com/auth/firebase.messaging")
	if err != nil {
		slog.Warn("FCM push disabled: invalid credentials", "error", err)
		return &Sender{enabled: false}
	}
	if projectID == "" {
		var parsed struct {
			ProjectID string `json:"project_id"`
		}
		_ = json.Unmarshal(data, &parsed)
		projectID = parsed.ProjectID
	}
	if projectID == "" {
		slog.Warn("FCM push disabled: FIREBASE_PROJECT_ID missing")
		return &Sender{enabled: false}
	}

	ts := conf.TokenSource(ctx)
	return &Sender{
		projectID: projectID,
		client:    &http.Client{Timeout: 10 * time.Second},
		tokenSrc:  ts,
		enabled:   true,
	}
}

func (s *Sender) Send(ctx context.Context, tokens []string, title, body string, data map[string]string) error {
	if s == nil || !s.enabled || len(tokens) == 0 {
		return nil
	}
	var firstErr error
	for _, token := range tokens {
		if err := s.sendOne(ctx, token, title, body, data); err != nil && firstErr == nil {
			firstErr = err
		}
	}
	return firstErr
}

func (s *Sender) sendOne(ctx context.Context, token, title, body string, data map[string]string) error {
	accessToken, err := s.accessToken(ctx)
	if err != nil {
		return err
	}
	if data == nil {
		data = map[string]string{}
	}
	payload := map[string]any{
		"message": map[string]any{
			"token": token,
			"notification": map[string]string{
				"title": title,
				"body":  body,
			},
			"data": data,
			"android": map[string]any{
				"priority": "high",
			},
		},
	}
	bodyBytes, err := json.Marshal(payload)
	if err != nil {
		return err
	}
	url := fmt.Sprintf("https://fcm.googleapis.com/v1/projects/%s/messages:send", s.projectID)
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(bodyBytes))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+accessToken)
	req.Header.Set("Content-Type", "application/json")

	resp, err := s.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	respBody, _ := io.ReadAll(io.LimitReader(resp.Body, 4096))
	if resp.StatusCode >= 300 {
		return fmt.Errorf("fcm status %d: %s", resp.StatusCode, string(respBody))
	}
	return nil
}

func (s *Sender) accessToken(ctx context.Context) (string, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	tok, err := s.tokenSrc.Token()
	if err != nil {
		return "", err
	}
	return tok.AccessToken, nil
}
