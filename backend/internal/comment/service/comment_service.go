package service

import (
	"context"
	"fmt"

	"github.com/xilo-platform/xilo/internal/comment/model"
	"github.com/xilo-platform/xilo/internal/comment/repository"
	"github.com/xilo-platform/xilo/pkg/validator"
)

type CommentService struct {
	repo *repository.CommentRepo
}

func NewCommentService(repo *repository.CommentRepo) *CommentService {
	return &CommentService{repo: repo}
}

func (s *CommentService) Create(ctx context.Context, postID, authorID string, req *model.CreateCommentRequest) (*model.Comment, error) {
	if verr := validator.ValidateComment(req.Content); verr != nil {
		return nil, fmt.Errorf("%s: %s", verr.Field, verr.Message)
	}

	if req.ParentID != nil && req.RootID == nil {
		req.RootID = req.ParentID
	}

	return s.repo.Create(ctx, postID, authorID, req)
}

func (s *CommentService) List(ctx context.Context, postID string, cursor string, limit int, sort string) ([]*model.Comment, string, error) {
	return s.repo.ListByPost(ctx, postID, cursor, limit, sort)
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
