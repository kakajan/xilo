package service

import (
	"fmt"
	"net/url"
	"strings"
	"unicode/utf8"

	"github.com/google/uuid"
	"github.com/xilo-platform/xilo/internal/post/model"
	"github.com/xilo-platform/xilo/pkg/validator"
)

const (
	PostTypeArticle = "article"
	PostTypeMicro   = "micro"
	PostTypePhoto   = "photo"
	PostTypeVideo   = "video"
	PostTypeLink    = "link"
	maxMicroRunes   = 500
)

// NormalizePostType maps an omitted type to article and rejects unknown values.
func NormalizePostType(raw string) (string, error) {
	t := strings.ToLower(strings.TrimSpace(raw))
	if t == "" {
		return PostTypeArticle, nil
	}
	switch t {
	case PostTypeArticle, PostTypeMicro, PostTypePhoto, PostTypeVideo, PostTypeLink:
		return t, nil
	default:
		return "", fmt.Errorf("unknown post_type")
	}
}

func ValidateTypedCreate(req *model.CreatePostRequest) error {
	t, err := NormalizePostType(req.PostType)
	if err != nil {
		return err
	}
	req.PostType = t
	req.MediaIDs = sanitizeMediaIDs(req.MediaIDs)
	req.LinkURL = strings.TrimSpace(req.LinkURL)
	return validateTypedFields(t, req.Title, req.ContentMD, req.Content, req.MediaIDs, req.LinkURL, req)
}

func ValidateTypedUpdate(existing *model.Post, req *model.UpdatePostRequest) error {
	t := existing.PostType
	if t == "" {
		t = PostTypeArticle
	}
	if req.PostType != nil {
		nt, err := NormalizePostType(*req.PostType)
		if err != nil {
			return err
		}
		t = nt
		req.PostType = &t
	}

	title := existing.Title
	if req.Title != nil {
		title = *req.Title
	}
	contentMD := existing.ContentMD
	if req.ContentMD != nil {
		contentMD = *req.ContentMD
	}
	content := existing.Content
	if req.Content != nil {
		content = *req.Content
	}
	media := append([]string(nil), existing.MediaIDs...)
	if req.MediaIDs != nil {
		media = sanitizeMediaIDs(*req.MediaIDs)
		cleaned := media
		req.MediaIDs = &cleaned
	}
	link := ""
	if existing.LinkURL != nil {
		link = *existing.LinkURL
	}
	if req.LinkURL != nil {
		link = strings.TrimSpace(*req.LinkURL)
		req.LinkURL = &link
	}

	synth := &model.CreatePostRequest{Title: title}
	if err := validateTypedFields(t, title, contentMD, content, media, link, synth); err != nil {
		return err
	}
	if req.Title == nil && synth.Title != existing.Title {
		titleCopy := synth.Title
		req.Title = &titleCopy
	}
	return nil
}

func validateTypedFields(
	t, title, contentMD, content string,
	mediaIDs []string,
	linkURL string,
	req *model.CreatePostRequest,
) error {
	switch t {
	case PostTypeArticle:
		if verr := validator.ValidateTitle(title); verr != nil {
			return fmt.Errorf("%s: %s", verr.Field, verr.Message)
		}
	case PostTypeMicro:
		body := strings.TrimSpace(contentMD)
		if body == "" {
			body = strings.TrimSpace(plainContentFallback(content))
		}
		if body == "" {
			return fmt.Errorf("micro body is required")
		}
		if utf8.RuneCountInString(body) > maxMicroRunes {
			return fmt.Errorf("micro body must be at most %d characters", maxMicroRunes)
		}
		if strings.TrimSpace(title) == "" && req != nil {
			req.Title = quoteTitleFromContent(body)
		}
	case PostTypePhoto:
		if n := len(mediaIDs); n < 1 || n > 10 {
			return fmt.Errorf("photo posts require 1-10 media_ids")
		}
		if err := requireUUIDs(mediaIDs); err != nil {
			return err
		}
		if strings.TrimSpace(title) == "" && req != nil {
			req.Title = "عکس"
		}
	case PostTypeVideo:
		if len(mediaIDs) != 1 {
			return fmt.Errorf("video posts require exactly 1 media_id")
		}
		if err := requireUUIDs(mediaIDs); err != nil {
			return err
		}
		if strings.TrimSpace(title) == "" && req != nil {
			req.Title = "ویدیو"
		}
	case PostTypeLink:
		if err := validateHTTPSURL(linkURL); err != nil {
			return err
		}
		if strings.TrimSpace(title) == "" && req != nil {
			req.Title = truncateRunes(linkURL, 80)
		}
	}
	return nil
}

func sanitizeMediaIDs(ids []string) []string {
	out := make([]string, 0, len(ids))
	seen := make(map[string]struct{}, len(ids))
	for _, id := range ids {
		id = strings.TrimSpace(id)
		if id == "" {
			continue
		}
		if _, ok := seen[id]; ok {
			continue
		}
		seen[id] = struct{}{}
		out = append(out, id)
	}
	return out
}

func requireUUIDs(ids []string) error {
	for _, id := range ids {
		if _, err := uuid.Parse(id); err != nil {
			return fmt.Errorf("invalid media_id")
		}
	}
	return nil
}

func validateHTTPSURL(raw string) error {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return fmt.Errorf("link_url is required")
	}
	parsed, err := url.Parse(raw)
	if err != nil || parsed.Host == "" || parsed.Scheme != "https" {
		return fmt.Errorf("link_url must be an https URL")
	}
	return nil
}

func plainContentFallback(content string) string {
	trimmed := strings.TrimSpace(content)
	if trimmed == "" || trimmed == "{}" || trimmed == "null" {
		return ""
	}
	return trimmed
}
