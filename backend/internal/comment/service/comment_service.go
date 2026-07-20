package service

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/xilo-platform/xilo/internal/comment/model"
	"github.com/xilo-platform/xilo/internal/comment/repository"
	notifrepo "github.com/xilo-platform/xilo/internal/notification/repository"
	notifsvc "github.com/xilo-platform/xilo/internal/notification/service"
	"github.com/xilo-platform/xilo/pkg/validator"
)

// CommentNotifier is the subset of notification service used after comment create.
type CommentNotifier interface {
	Notify(ctx context.Context, req notifsvc.NotifyRequest) (*notifrepo.Notification, error)
}

type CommentService struct {
	repo  *repository.CommentRepo
	notif CommentNotifier
}

func NewCommentService(repo *repository.CommentRepo) *CommentService {
	return &CommentService{repo: repo}
}

func (s *CommentService) SetNotifier(n CommentNotifier) {
	s.notif = n
}

func (s *CommentService) Create(ctx context.Context, postID, authorID string, req *model.CreateCommentRequest) (*model.Comment, error) {
	if verr := validator.ValidateComment(req.Content); verr != nil {
		return nil, fmt.Errorf("%s: %s", verr.Field, verr.Message)
	}

	if req.ParentID != nil && req.RootID == nil {
		req.RootID = req.ParentID
	}

	comment, err := s.repo.Create(ctx, postID, authorID, req)
	if err != nil {
		return nil, err
	}
	s.notifyAfterCreate(ctx, comment, authorID)
	return comment, nil
}

func (s *CommentService) notifyAfterCreate(ctx context.Context, comment *model.Comment, actorID string) {
	if comment == nil {
		return
	}
	if comment.ParentID != nil && *comment.ParentID != "" {
		s.notifyReply(ctx, comment, actorID)
		return
	}
	s.notifyPostComment(ctx, comment, actorID)
}

func (s *CommentService) notifyReply(ctx context.Context, comment *model.Comment, actorID string) {
	if s.notif == nil || comment == nil || comment.ParentID == nil {
		return
	}
	parent, err := s.repo.GetByID(ctx, *comment.ParentID)
	if err != nil || parent == nil {
		return
	}
	excerpt := truncateExcerpt(comment.Content)
	data := map[string]any{
		"comment_id": comment.ID,
		"parent_id":  *comment.ParentID,
		"post_id":    comment.PostID,
		"author_id":  actorID,
		"type":       notifsvc.TypeCommentReply,
	}
	if _, username, slug, err := s.repo.GetPostNotifyTarget(ctx, comment.PostID); err == nil {
		if slug != "" {
			data["slug"] = slug
			data["post_slug"] = slug
		}
		if username != "" {
			data["post_author_username"] = username
		}
	}
	if _, err := s.notif.Notify(ctx, notifsvc.NotifyRequest{
		RecipientID: parent.AuthorID,
		ActorID:     actorID,
		Type:        notifsvc.TypeCommentReply,
		Title:       "New reply to your comment",
		Body:        excerpt,
		Data:        data,
	}); err != nil {
		slog.Warn("comment reply notification failed", "comment_id", comment.ID, "error", err)
	}
}

func (s *CommentService) notifyPostComment(ctx context.Context, comment *model.Comment, actorID string) {
	if s.notif == nil || comment == nil {
		return
	}
	authorID, username, slug, err := s.repo.GetPostNotifyTarget(ctx, comment.PostID)
	if err != nil || authorID == "" {
		slog.Warn("post comment notification skipped", "comment_id", comment.ID, "error", err)
		return
	}
	excerpt := truncateExcerpt(comment.Content)
	data := map[string]any{
		"comment_id": comment.ID,
		"post_id":    comment.PostID,
		"author_id":  actorID,
		"type":       notifsvc.TypePostComment,
	}
	if slug != "" {
		data["slug"] = slug
		data["post_slug"] = slug
	}
	if username != "" {
		data["post_author_username"] = username
	}
	if _, err := s.notif.Notify(ctx, notifsvc.NotifyRequest{
		RecipientID: authorID,
		ActorID:     actorID,
		Type:        notifsvc.TypePostComment,
		Title:       "New comment on your post",
		Body:        excerpt,
		Data:        data,
	}); err != nil {
		slog.Warn("post comment notification failed", "comment_id", comment.ID, "error", err)
	}
}

func truncateExcerpt(content string) string {
	if len(content) > 120 {
		return content[:120] + "…"
	}
	return content
}

func (s *CommentService) List(ctx context.Context, postID string, cursor string, limit int, sort string, viewerID string) ([]*model.Comment, string, error) {
	return s.repo.ListByPost(ctx, postID, cursor, limit, sort, viewerID)
}

func (s *CommentService) Update(ctx context.Context, commentID, userID, content string) (*model.Comment, error) {
	comment, err := s.repo.GetByID(ctx, commentID)
	if err != nil {
		return nil, err
	}

	if comment.AuthorID != userID {
		return nil, fmt.Errorf("only the author can edit this comment")
	}

	return s.repo.Update(ctx, commentID, content)
}

func (s *CommentService) Delete(ctx context.Context, commentID, userID string) error {
	comment, err := s.repo.GetByID(ctx, commentID)
	if err != nil {
		return err
	}

	if comment.AuthorID != userID {
		return fmt.Errorf("only the author can delete this comment")
	}

	return s.repo.Delete(ctx, commentID)
}

func (s *CommentService) Pin(ctx context.Context, commentID string, pin bool) error {
	return s.repo.Pin(ctx, commentID, pin)
}

func (s *CommentService) ToggleReaction(ctx context.Context, userID, targetType, targetID, reaction string) (int, error) {
	return s.repo.ToggleReaction(ctx, userID, targetType, targetID, reaction)
}
