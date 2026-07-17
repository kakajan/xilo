package service

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"golang.org/x/sync/singleflight"

	pkgredis "github.com/xilo-platform/xilo/pkg/redis"
	"github.com/xilo-platform/xilo/internal/post/model"
	"github.com/xilo-platform/xilo/pkg/i18n"
	"github.com/xilo-platform/xilo/pkg/validator"
)

type PostRepository interface {
	Create(ctx context.Context, req *model.CreatePostRequest, authorID string) (*model.Post, error)
	GetBySlug(ctx context.Context, slug string) (*model.Post, error)
	GetByID(ctx context.Context, id string) (*model.Post, error)
	Update(ctx context.Context, id string, req *model.UpdatePostRequest) (*model.Post, error)
	Delete(ctx context.Context, id string) error
	List(ctx context.Context, params model.PostListParams) ([]*model.Post, string, error)
	EnrichPosts(ctx context.Context, posts []*model.Post, viewerID string) error
}

type PostService struct {
	repo PostRepository
	rdb  *pkgredis.Client
	sf   singleflight.Group
}

func NewPostService(repo PostRepository, rdb *pkgredis.Client) *PostService {
	return &PostService{repo: repo, rdb: rdb}
}

func (s *PostService) Create(ctx context.Context, authorID string, req *model.CreatePostRequest) (*model.Post, error) {
	if verr := validator.ValidateTitle(req.Title); verr != nil {
		return nil, fmt.Errorf("%s: %s", verr.Field, verr.Message)
	}
	if len(req.Tags) > 0 {
		if verrs := validator.ValidateTags(req.Tags); len(verrs) > 0 {
			return nil, fmt.Errorf("tags: %s", verrs[0].Message)
		}
	}
	if req.Status == "" {
		req.Status = "draft"
	}
	if req.Language == "" {
		req.Language = i18n.DefaultLanguage
	}
	if !i18n.IsValidLanguage(req.Language) {
		return nil, fmt.Errorf("invalid language code: %s", req.Language)
	}
	return s.repo.Create(ctx, req, authorID)
}

func (s *PostService) GetBySlug(ctx context.Context, slug string, viewerID string) (*model.Post, error) {
	cacheKey := "post:" + slug

	var post *model.Post
	cached, err := s.rdb.Get(ctx, cacheKey).Result()
	if err == nil {
		var cachedPost model.Post
		if err := json.Unmarshal([]byte(cached), &cachedPost); err == nil {
			post = &cachedPost
		} else {
			slog.Info("corrupted cache entry, evicting", "key", cacheKey)
			s.rdb.Del(ctx, cacheKey)
		}
	}

	if post == nil {
		v, err, _ := s.sf.Do(cacheKey, func() (interface{}, error) {
			loaded, err := s.repo.GetBySlug(ctx, slug)
			if err != nil {
				return nil, err
			}

			data, _ := json.Marshal(loaded)
			s.rdb.Set(ctx, cacheKey, data, 5*time.Minute)

			return loaded, nil
		})
		if err != nil {
			return nil, err
		}
		post = v.(*model.Post)
	}

	// Copy before viewer-specific enrichment so concurrent requests do not share state.
	result := *post
	if err := s.repo.EnrichPosts(ctx, []*model.Post{&result}, viewerID); err != nil {
		slog.Warn("failed to enrich post", "slug", slug, "error", err)
	}
	return &result, nil
}

func (s *PostService) GetByID(ctx context.Context, id string) (*model.Post, error) {
	return s.repo.GetByID(ctx, id)
}

func (s *PostService) Update(ctx context.Context, id string, userID string, req *model.UpdatePostRequest) (*model.Post, error) {
	post, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return nil, err
	}
	if post.AuthorID != userID {
		return nil, fmt.Errorf("only the author can edit this post")
	}

	updated, err := s.repo.Update(ctx, id, req)
	if err != nil {
		return nil, err
	}

	s.rdb.Del(ctx, "post:"+post.Slug)
	if req.Slug != nil {
		s.rdb.Del(ctx, "post:"+*req.Slug)
	}

	return updated, nil
}

func (s *PostService) Delete(ctx context.Context, id string, userID string) error {
	post, err := s.repo.GetByID(ctx, id)
	if err != nil {
		return err
	}
	if post.AuthorID != userID {
		return fmt.Errorf("only the author can delete this post")
	}
	if err := s.repo.Delete(ctx, id); err != nil {
		return err
	}
	s.rdb.Del(ctx, "post:"+post.Slug)
	return nil
}

func (s *PostService) List(ctx context.Context, params model.PostListParams) ([]*model.Post, string, error) {
	if params.Limit <= 0 || params.Limit > 50 {
		params.Limit = 10
	}
	return s.repo.List(ctx, params)
}
