package service

import (
	"context"
	"testing"

	"github.com/lib/pq"
	pkgredis "github.com/xilo-platform/xilo/pkg/redis"
	"github.com/xilo-platform/xilo/internal/post/model"
)

type mockPostRepo struct {
	posts map[string]*model.Post
}

func newMockPostRepo() *mockPostRepo {
	return &mockPostRepo{posts: make(map[string]*model.Post)}
}

func (m *mockPostRepo) Create(ctx context.Context, req *model.CreatePostRequest, authorID string) (*model.Post, error) {
	post := &model.Post{
		ID:        "post-1",
		AuthorID:  authorID,
		Title:     req.Title,
		Slug:      req.Slug,
		Status:    req.Status,
		ContentMD: req.ContentMD,
		Content:   req.Content,
		Tags:      pq.StringArray(req.Tags),
	}
	if req.AudioURL != "" {
		u := req.AudioURL
		post.AudioURL = &u
	}
	m.posts[post.ID] = post
	return post, nil
}

func (m *mockPostRepo) GetBySlug(ctx context.Context, slug string) (*model.Post, error) {
	for _, p := range m.posts {
		if p.Slug == slug {
			return p, nil
		}
	}
	return nil, nil
}

func (m *mockPostRepo) GetByID(ctx context.Context, id string) (*model.Post, error) {
	p, ok := m.posts[id]
	if !ok {
		return nil, nil
	}
	return p, nil
}

func (m *mockPostRepo) Update(ctx context.Context, id string, req *model.UpdatePostRequest) (*model.Post, error) {
	p := m.posts[id]
	if p == nil {
		return nil, nil
	}
	if req.Title != nil {
		p.Title = *req.Title
	}
	if req.ContentMD != nil {
		p.ContentMD = *req.ContentMD
	}
	if req.Content != nil {
		p.Content = *req.Content
	}
	if req.Tags != nil {
		p.Tags = *req.Tags
	}
	if req.AudioURL != nil {
		if *req.AudioURL == "" {
			p.AudioURL = nil
		} else {
			u := *req.AudioURL
			p.AudioURL = &u
		}
	}
	return p, nil
}

func (m *mockPostRepo) SuggestTags(ctx context.Context, query string, limit int) ([]model.TagSuggestion, error) {
	return nil, nil
}

func (m *mockPostRepo) TrendingTags(ctx context.Context, limit int) ([]model.TagSuggestion, error) {
	return nil, nil
}

func (m *mockPostRepo) Delete(ctx context.Context, id string) error {
	delete(m.posts, id)
	return nil
}

func (m *mockPostRepo) List(ctx context.Context, params model.PostListParams) ([]*model.Post, string, error) {
	var result []*model.Post
	for _, p := range m.posts {
		result = append(result, p)
	}
	return result, "", nil
}

func (m *mockPostRepo) EnrichPosts(ctx context.Context, posts []*model.Post, viewerID string) error {
	return nil
}

func (m *mockPostRepo) RecordView(ctx context.Context, postID, userID, sessionID string) (*model.RecordViewResult, error) {
	p := m.posts[postID]
	if p == nil {
		return nil, nil
	}
	if userID != "" && userID == p.AuthorID {
		return &model.RecordViewResult{Counted: false, ViewCount: p.ViewCount, Slug: p.Slug}, nil
	}
	p.ViewCount++
	return &model.RecordViewResult{Counted: true, ViewCount: p.ViewCount, Slug: p.Slug}, nil
}

func TestRecordView_Increments(t *testing.T) {
	repo := newMockPostRepo()
	repo.posts["post-1"] = &model.Post{
		ID: "post-1", AuthorID: "author-1", Slug: "hello", ViewCount: 2,
	}
	svc := NewPostService(repo, nil)

	result, err := svc.RecordView(context.Background(), "post-1", "reader-1", "session-abcdefgh")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !result.Counted || result.ViewCount != 3 {
		t.Fatalf("expected counted view_count=3, got %+v", result)
	}
}

func TestRecordView_SkipsAuthor(t *testing.T) {
	repo := newMockPostRepo()
	repo.posts["post-1"] = &model.Post{
		ID: "post-1", AuthorID: "author-1", Slug: "hello", ViewCount: 5,
	}
	svc := NewPostService(repo, nil)

	result, err := svc.RecordView(context.Background(), "post-1", "author-1", "session-abcdefgh")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Counted || result.ViewCount != 5 {
		t.Fatalf("expected author skip, got %+v", result)
	}
}

func TestCreatePost_Valid(t *testing.T) {
	repo := newMockPostRepo()
	rdb := &pkgredis.Client{}
	svc := NewPostService(repo, rdb)

	post, err := svc.Create(context.Background(), "author-1", &model.CreatePostRequest{
		Title:  "Test Post",
		Status: "draft",
	})
	if err != nil {
		t.Fatalf("expected no error, got %v", err)
	}
	if post.Title != "Test Post" {
		t.Errorf("expected 'Test Post', got %s", post.Title)
	}
}

func TestCreatePost_EmptyTitle(t *testing.T) {
	repo := newMockPostRepo()
	rdb := &pkgredis.Client{}
	svc := NewPostService(repo, rdb)

	_, err := svc.Create(context.Background(), "author-1", &model.CreatePostRequest{
		Title: "",
	})
	if err == nil {
		t.Fatal("expected error for empty title")
	}
}

func TestCreatePost_WithAudioURL(t *testing.T) {
	repo := newMockPostRepo()
	svc := NewPostService(repo, nil)

	post, err := svc.Create(context.Background(), "author-1", &model.CreatePostRequest{
		Title:    "Audio Post",
		Status:   "draft",
		AudioURL: "https://cdn.example/post.mp3",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if post.AudioURL == nil || *post.AudioURL != "https://cdn.example/post.mp3" {
		t.Fatalf("expected audio_url, got %v", post.AudioURL)
	}
}

func TestCreatePost_ExtractsHashtags(t *testing.T) {
	repo := newMockPostRepo()
	svc := NewPostService(repo, nil)

	post, err := svc.Create(context.Background(), "author-1", &model.CreatePostRequest{
		Title:     "Hashtag post",
		ContentMD: "متن با #خبر و #Xilo_App",
		Tags:      []string{"manual"},
		Status:    "published",
	})
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if len(post.Tags) != 3 {
		t.Fatalf("expected 3 tags, got %v", post.Tags)
	}
	if post.Tags[0] != "خبر" || post.Tags[1] != "xilo_app" || post.Tags[2] != "manual" {
		t.Fatalf("unexpected tag order/values: %v", post.Tags)
	}
}
