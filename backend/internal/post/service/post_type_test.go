package service

import (
	"strings"
	"testing"

	"github.com/xilo-platform/xilo/internal/post/model"
)

func TestNormalizePostType(t *testing.T) {
	got, err := NormalizePostType("")
	if err != nil || got != PostTypeArticle {
		t.Fatalf("empty: got %q %v", got, err)
	}
	got, err = NormalizePostType("MICRO")
	if err != nil || got != PostTypeMicro {
		t.Fatalf("micro: got %q %v", got, err)
	}
	if _, err := NormalizePostType("story"); err == nil {
		t.Fatal("expected unknown post_type to fail")
	}
}

func TestValidateTypedCreate_DefaultArticleNeedsTitle(t *testing.T) {
	req := &model.CreatePostRequest{ContentMD: "body"}
	if err := ValidateTypedCreate(req); err == nil {
		t.Fatal("expected title required for article")
	}
	req.Title = "Hello"
	if err := ValidateTypedCreate(req); err != nil {
		t.Fatalf("article: %v", err)
	}
	if req.PostType != PostTypeArticle {
		t.Fatalf("post_type = %q", req.PostType)
	}
}

func TestValidateTypedCreate_MicroAllowsEmptyTitle(t *testing.T) {
	req := &model.CreatePostRequest{PostType: "micro", ContentMD: "سلام دنیا"}
	if err := ValidateTypedCreate(req); err != nil {
		t.Fatalf("micro: %v", err)
	}
	if req.Title == "" {
		t.Fatal("expected fallback title")
	}
	empty := &model.CreatePostRequest{PostType: "micro"}
	if err := ValidateTypedCreate(empty); err == nil {
		t.Fatal("expected micro body required")
	}
	req.ContentMD = strings.Repeat("a", 501)
	req.Title = ""
	if err := ValidateTypedCreate(req); err == nil {
		t.Fatal("expected micro length error")
	}
}

func TestValidateTypedCreate_PhotoAndVideoAndLink(t *testing.T) {
	photo := &model.CreatePostRequest{
		PostType: "photo",
		MediaIDs: []string{"11111111-1111-1111-1111-111111111111"},
	}
	if err := ValidateTypedCreate(photo); err != nil {
		t.Fatalf("photo: %v", err)
	}
	if err := ValidateTypedCreate(&model.CreatePostRequest{PostType: "photo"}); err == nil {
		t.Fatal("expected photo media_ids")
	}
	video := &model.CreatePostRequest{
		PostType: "video",
		MediaIDs: []string{
			"11111111-1111-1111-1111-111111111111",
			"22222222-2222-2222-2222-222222222222",
		},
	}
	if err := ValidateTypedCreate(video); err == nil {
		t.Fatal("expected video single media_id")
	}
	link := &model.CreatePostRequest{PostType: "link", LinkURL: "http://example.com"}
	if err := ValidateTypedCreate(link); err == nil {
		t.Fatal("expected https")
	}
	link.LinkURL = "https://example.com/x"
	if err := ValidateTypedCreate(link); err != nil {
		t.Fatalf("link: %v", err)
	}
}
