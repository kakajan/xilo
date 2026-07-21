package discover

import "strings"

const parentPreviewMaxRunes = 140

// ParentContext is a short summary of the comment being replied to.
type ParentContext struct {
	ID                 string `json:"id"`
	AuthorUsername     string `json:"author_username"`
	AuthorDisplayName  string `json:"author_display_name"`
	ContentPreview     string `json:"content_preview"`
}

func truncatePreview(content string, maxRunes int) string {
	content = strings.TrimSpace(content)
	if content == "" || maxRunes <= 0 {
		return ""
	}
	runes := []rune(content)
	if len(runes) <= maxRunes {
		return content
	}
	return string(runes[:maxRunes]) + "…"
}

// buildParentContext returns a parent summary when parent ID and username are present.
func buildParentContext(id, username, displayName, content string) *ParentContext {
	id = strings.TrimSpace(id)
	username = strings.TrimSpace(username)
	if id == "" || username == "" {
		return nil
	}
	displayName = strings.TrimSpace(displayName)
	if displayName == "" {
		displayName = username
	}
	return &ParentContext{
		ID:                id,
		AuthorUsername:    username,
		AuthorDisplayName: displayName,
		ContentPreview:    truncatePreview(content, parentPreviewMaxRunes),
	}
}
