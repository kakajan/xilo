package service

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/xilo-platform/xilo/internal/comment/model"
	"github.com/xilo-platform/xilo/internal/comment/repository"
	notifsvc "github.com/xilo-platform/xilo/internal/notification/service"
	"github.com/xilo-platform/xilo/pkg/validator"
)

type CommentService struct {
	repo  *repository.CommentRepo
	notif *notifsvc.NotificationService
}

func NewCommentService(repo *repository.CommentRepo) *CommentService {
	return &CommentService{repo: repo}
}

func (s *CommentService) SetNotifier(n *notifsvc.NotificationService) {
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
	s.notifyReply(ctx, comment, authorID)
	return comment, nil
}

func (s *CommentService) notifyReply(ctx context.Context, comment *model.Comment, actorID string) {
	if s.notif == nil || comment == nil || comment.ParentID == nil {
		return
	}
	parent, err := s.repo.GetByID(ctx, *comment.ParentID)
	if err != nil || parent == nil {
		return
	}
	excerpt := comment.Content
	if len(excerpt) > 120 {
		excerpt = excerpt[:120] + "…"
	}
	if _, err := s.notif.Notify(ctx, notifsvc.NotifyRequest{
		RecipientID: parent.AuthorID,
		ActorID:     actorID,
		Type:        notifsvc.TypeCommentReply,
		Title:       "New reply to your comment",
		Body:        excerpt,
		Data: map[string]any{
			"comment_id": comment.ID,
			"parent_id":  *comment.ParentID,
			"post_id":    comment.PostID,
			"author_id":  actorID,
		},
	}); err != nil {
		slog.Warn("comment reply notification failed", "comment_id", comment.ID, "error", err)
	}
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
