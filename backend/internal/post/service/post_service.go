package service

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"strings"
	"time"

	"golang.org/x/sync/singleflight"

	notifsvc "github.com/xilo-platform/xilo/internal/notification/service"
	"github.com/xilo-platform/xilo/internal/post/model"
	"github.com/xilo-platform/xilo/pkg/hashtag"
	"github.com/xilo-platform/xilo/pkg/i18n"
	pkgredis "github.com/xilo-platform/xilo/pkg/redis"
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
	RecordRepost(ctx context.Context, userID, postID string) error
	RecordView(ctx context.Context, postID, userID, sessionID string) (*model.RecordViewResult, error)
	SuggestTags(ctx context.Context, query string, limit int) ([]model.TagSuggestion, error)
	TrendingTags(ctx context.Context, limit int) ([]model.TagSuggestion, error)
}

type PostService struct {
	repo  PostRepository
	rdb   *pkgredis.Client
	sf    singleflight.Group
	notif *notifsvc.NotificationService
}

func NewPostService(repo PostRepository, rdb *pkgredis.Client) *PostService {
	return &PostService{repo: repo, rdb: rdb}
}

func (s *PostService) SetNotifier(n *notifsvc.NotificationService) {
	s.notif = n
}

func (s *PostService) Create(ctx context.Context, authorID string, req *model.CreatePostRequest) (*model.Post, error) {
	quotedID := ""
	if req.QuotedPostID != "" {
		quotedID = req.QuotedPostID
		quoted, err := s.repo.GetByID(ctx, quotedID)
		if err != nil || quoted == nil {
			return nil, fmt.Errorf("quoted post not found")
		}
		if quoted.Status != "published" || quoted.DeletedAt != nil {
			return nil, fmt.Errorf("quoted post not found")
		}
		req.QuotedPostID = quoted.ID
		if req.Status == "" {
			req.Status = "published"
		}
		if req.Title == "" {
			req.Title = quoteTitleFromContent(req.ContentMD)
		}
		if req.Excerpt == "" && req.ContentMD != "" {
			runes := []rune(req.ContentMD)
			if len(runes) > 200 {
				req.Excerpt = string(runes[:200])
			} else {
				req.Excerpt = req.ContentMD
			}
		}
	}

	if verr := validator.ValidateTitle(req.Title); verr != nil {
		return nil, fmt.Errorf("%s: %s", verr.Field, verr.Message)
	}
	applyHashtagsCreate(req)
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
	post, err := s.repo.Create(ctx, req, authorID)
	if err != nil {
		return nil, err
	}
	if quotedID != "" && post.Status == "published" {
		if err := s.repo.RecordRepost(ctx, authorID, quotedID); err != nil {
			slog.Warn("failed to record quote repost", "quoted_post_id", quotedID, "error", err)
		}
	}
	if post.Status == "published" {
		s.notifyFollowersPublished(post)
	}
	_ = s.repo.EnrichPosts(ctx, []*model.Post{post}, authorID)
	return post, nil
}

func quoteTitleFromContent(contentMD string) string {
	text := strings.TrimSpace(contentMD)
	if text == "" {
		return "نقل‌قول"
	}
	runes := []rune(text)
	if len(runes) > 80 {
		return string(runes[:80]) + "…"
	}
	return text
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

	if err := applyHashtagsUpdate(post, req); err != nil {
		return nil, err
	}

	wasPublished := post.Status == "published"
	updated, err := s.repo.Update(ctx, id, req)
	if err != nil {
		return nil, err
	}

	if s.rdb != nil {
		s.rdb.Del(ctx, "post:"+post.Slug)
		if req.Slug != nil {
			s.rdb.Del(ctx, "post:"+*req.Slug)
		}
	}

	if !wasPublished && updated.Status == "published" {
		s.notifyFollowersPublished(updated)
	}

	return updated, nil
}

func (s *PostService) notifyFollowersPublished(post *model.Post) {
	if s.notif == nil || post == nil {
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()
		followerIDs, err := s.notif.ListFollowerIDs(ctx, post.AuthorID)
		if err != nil {
			slog.Warn("list followers for post notification", "post_id", post.ID, "error", err)
			return
		}
		const batch = 100
		title := "New post"
		body := post.Title
		data := map[string]any{
			"post_id":   post.ID,
			"author_id": post.AuthorID,
			"title":     post.Title,
			"slug":      post.Slug,
		}
		for i := 0; i < len(followerIDs); i += batch {
			end := i + batch
			if end > len(followerIDs) {
				end = len(followerIDs)
			}
			s.notif.NotifyMany(ctx, post.AuthorID, notifsvc.TypePostPublished, title, body, data, followerIDs[i:end])
		}
	}()
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

func (s *PostService) RecordView(ctx context.Context, postID, userID, sessionID string) (*model.RecordViewResult, error) {
	result, err := s.repo.RecordView(ctx, postID, userID, sessionID)
	if err != nil {
		return nil, err
	}
	if result.Counted && result.Slug != "" && s.rdb != nil {
		s.rdb.Del(ctx, "post:"+result.Slug)
	}
	return result, nil
}

func (s *PostService) SuggestTags(ctx context.Context, query string, limit int) ([]model.TagSuggestion, error) {
	if limit <= 0 || limit > 20 {
		limit = 10
	}
	return s.repo.SuggestTags(ctx, query, limit)
}

func (s *PostService) TrendingTags(ctx context.Context, limit int) ([]model.TagSuggestion, error) {
	if limit <= 0 || limit > 50 {
		limit = 20
	}
	return s.repo.TrendingTags(ctx, limit)
}

func applyHashtagsCreate(req *model.CreatePostRequest) {
	text := hashtag.PlainTextFromContent(req.ContentMD, req.Content)
	req.Tags = hashtag.Merge(hashtag.Extract(text), req.Tags)
}

func applyHashtagsUpdate(existing *model.Post, req *model.UpdatePostRequest) error {
	contentChanged := req.Content != nil || req.ContentMD != nil
	tagsProvided := req.Tags != nil
	if !contentChanged && !tagsProvided {
		return nil
	}

	text := existing.ContentMD
	if req.ContentMD != nil {
		text = *req.ContentMD
	} else if req.Content != nil {
		text = hashtag.PlainTextFromContent("", *req.Content)
	}

	var clientTags []string
	switch {
	case tagsProvided:
		clientTags = *req.Tags
	case contentChanged:
		// Content-only update: tags become whatever is in the new text.
		clientTags = nil
	default:
		clientTags = append([]string(nil), existing.Tags...)
	}

	merged := hashtag.Merge(hashtag.Extract(text), clientTags)
	if len(merged) > 0 {
		if verrs := validator.ValidateTags(merged); len(verrs) > 0 {
			return fmt.Errorf("tags: %s", verrs[0].Message)
		}
	}
	req.Tags = &merged
	return nil
}
