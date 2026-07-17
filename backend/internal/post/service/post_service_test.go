package service

import (
	"context"
	"testing"

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
		ID:       "post-1",
		AuthorID: authorID,
		Title:    req.Title,
		Slug:     req.Slug,
		Status:   req.Status,
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
	return p, nil
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
