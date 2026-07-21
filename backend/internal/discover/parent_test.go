package discover

import (
	"database/sql"
	"strings"
	"testing"
	"time"
)

func TestTruncatePreview(t *testing.T) {
	if got := truncatePreview("  hello  ", 140); got != "hello" {
		t.Fatalf("truncatePreview short = %q", got)
	}
	long := strings.Repeat("آ", 145)
	got := truncatePreview(long, 140)
	if !strings.HasSuffix(got, "…") {
		t.Fatalf("expected ellipsis, got %q", got)
	}
	if len([]rune(got)) != 141 { // 140 + ellipsis
		t.Fatalf("rune length = %d, want 141", len([]rune(got)))
	}
}

func TestBuildParentContext_RootOmitsParent(t *testing.T) {
	if got := buildParentContext("", "alice", "Alice", "hi"); got != nil {
		t.Fatalf("expected nil for empty id, got %#v", got)
	}
	if got := buildParentContext("p1", "", "Alice", "hi"); got != nil {
		t.Fatalf("expected nil for empty username, got %#v", got)
	}
}

func TestBuildParentContext_Reply(t *testing.T) {
	got := buildParentContext("p1", "alice", "", "parent body")
	if got == nil {
		t.Fatal("expected parent context")
	}
	if got.ID != "p1" || got.AuthorUsername != "alice" || got.AuthorDisplayName != "alice" {
		t.Fatalf("unexpected parent: %#v", got)
	}
	if got.ContentPreview != "parent body" {
		t.Fatalf("preview = %q", got.ContentPreview)
	}
}

func TestCommentRowToComment_RootVsReply(t *testing.T) {
	root := commentRow{
		ID:          "c-root",
		Content:     "top",
		CreatedAt:   time.Unix(1, 0).UTC(),
		AuthorID:    "u1",
		Username:    "bob",
		DisplayName: "Bob",
		PostID:      "post1",
		PostTitle:   "Hello",
		PostSlug:    "hello",
		Depth:       0,
	}.toComment()
	if root.ParentID != nil || root.Parent != nil || root.Depth != 0 {
		t.Fatalf("root comment unexpected parent fields: %#v", root)
	}

	parentID := "c-parent"
	reply := commentRow{
		ID:          "c-reply",
		Content:     "nested",
		CreatedAt:   time.Unix(2, 0).UTC(),
		AuthorID:    "u2",
		Username:    "carol",
		DisplayName: "Carol",
		PostID:      "post1",
		PostTitle:   "Hello",
		PostSlug:    "hello",
		Depth:       2,
		ParentID:    sql.NullString{String: parentID, Valid: true},
		RootID:      sql.NullString{String: "c-root", Valid: true},
		ParentCommentID: sql.NullString{String: parentID, Valid: true},
		ParentContent:   sql.NullString{String: "parent text", Valid: true},
		ParentAuthorUsername: sql.NullString{String: "alice", Valid: true},
		ParentAuthorDisplayName: sql.NullString{String: "Alice", Valid: true},
	}.toComment()

	if reply.ParentID == nil || *reply.ParentID != parentID {
		t.Fatalf("parent_id = %v", reply.ParentID)
	}
	if reply.RootID == nil || *reply.RootID != "c-root" {
		t.Fatalf("root_id = %v", reply.RootID)
	}
	if reply.Depth != 2 || reply.Parent == nil {
		t.Fatalf("reply enrichment failed: %#v", reply)
	}
	if reply.Parent.AuthorUsername != "alice" || reply.Parent.ContentPreview != "parent text" {
		t.Fatalf("parent summary: %#v", reply.Parent)
	}
}

func TestCommentRowToComment_DeletedParentOmitsSummary(t *testing.T) {
	parentID := "gone"
	c := commentRow{
		ID:       "c-reply",
		Content:  "nested",
		AuthorID: "u2",
		Username: "carol",
		PostID:   "post1",
		Depth:    1,
		ParentID: sql.NullString{String: parentID, Valid: true},
		// LEFT JOIN miss → parent_comment_id invalid
	}.toComment()
	if c.ParentID == nil || *c.ParentID != parentID {
		t.Fatalf("parent_id should still be set, got %v", c.ParentID)
	}
	if c.Parent != nil {
		t.Fatalf("parent summary should be omitted when parent row missing, got %#v", c.Parent)
	}
}
