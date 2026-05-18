package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"

	"github.com/jmoiron/sqlx"
	"github.com/xilo-platform/xilo/internal/comment/model"
)

var ErrCommentNotFound = errors.New("comment not found")

type CommentRepo struct {
	db *sqlx.DB
}

func NewCommentRepo(db *sqlx.DB) *CommentRepo {
	return &CommentRepo{db: db}
}

func (r *CommentRepo) Create(ctx context.Context, postID, authorID string, req *model.CreateCommentRequest) (*model.Comment, error) {
	depth := 0
	if req.ParentID != nil {
		var parentDepth int
		err := r.db.GetContext(ctx, &parentDepth,
			`SELECT depth FROM comments WHERE id = $1`, *req.ParentID)
		if err != nil {
			return nil, fmt.Errorf("get parent depth: %w", err)
		}
		depth = parentDepth + 1
		if depth > 4 {
			depth = 4
		}
	}

	var comment model.Comment
	err := r.db.GetContext(ctx, &comment, `
		INSERT INTO comments (post_id, author_id, parent_id, root_id, depth, content, content_html, media_url)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
		RETURNING id, post_id, author_id, parent_id, root_id, depth, content, content_html,
		          media_url, is_pinned, is_spam, created_at, updated_at
	`, postID, authorID, req.ParentID, req.RootID, depth, req.Content, req.Content, req.MediaURL)
	if err != nil {
		return nil, fmt.Errorf("insert comment: %w", err)
	}
	return &comment, nil
}

func (r *CommentRepo) GetByID(ctx context.Context, id string) (*model.Comment, error) {
	var comment model.Comment
	err := r.db.GetContext(ctx, &comment, `
		SELECT c.id, c.post_id, c.author_id, c.parent_id, c.root_id, c.depth,
		       c.content, c.content_html, c.media_url, c.is_pinned, c.is_spam,
		       c.created_at, c.updated_at,
		       u.id, u.username, u.display_name, u.avatar_url
		FROM comments c
		JOIN users u ON c.author_id = u.id
		WHERE c.id = $1 AND c.deleted_at IS NULL
	`, id)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrCommentNotFound
		}
		return nil, fmt.Errorf("get comment by id: %w", err)
	}
	return &comment, nil
}

func (r *CommentRepo) ListByPost(ctx context.Context, postID string, cursor string, limit int, sort string) ([]*model.Comment, string, error) {
	if limit <= 0 || limit > 50 {
		limit = 20
	}

	allComments, err := r.getAllComments(ctx, postID)
	if err != nil {
		return nil, "", err
	}

	commentMap := make(map[string]*model.Comment)
	var roots []*model.Comment

	for _, c := range allComments {
		commentMap[c.ID] = c
	}

	for _, c := range allComments {
		if c.ParentID == nil || *c.ParentID == "" {
			roots = append(roots, c)
		} else {
			if parent, ok := commentMap[*c.ParentID]; ok {
				parent.Replies = append(parent.Replies, c)
			} else {
				roots = append(roots, c)
			}
		}
	}

	if cursor != "" {
		var newRoots []*model.Comment
		found := false
		for _, r := range roots {
			if r.ID == cursor {
				found = true
				continue
			}
			if found {
				newRoots = append(newRoots, r)
			}
		}
		if !found {
			newRoots = roots
		}
		roots = newRoots
	}

	var nextCursor string
	if len(roots) > limit {
		roots = roots[:limit]
		nextCursor = roots[len(roots)-1].ID
	}

	return roots, nextCursor, nil
}

func (r *CommentRepo) getAllComments(ctx context.Context, postID string) ([]*model.Comment, error) {
	var comments []*model.Comment
	err := r.db.SelectContext(ctx, &comments, `
		SELECT c.id, c.post_id, c.author_id, c.parent_id, c.root_id, c.depth,
		       c.content, c.content_html, c.media_url, c.is_pinned, c.is_spam,
		       c.created_at, c.updated_at,
		       u.id, u.username, u.display_name, u.avatar_url
		FROM comments c
		JOIN users u ON c.author_id = u.id
		WHERE c.post_id = $1 AND c.deleted_at IS NULL
		ORDER BY c.created_at ASC
		LIMIT 500
	`, postID)
	if err != nil {
		return nil, fmt.Errorf("get all comments: %w", err)
	}
	return comments, nil
}

func (r *CommentRepo) Update(ctx context.Context, id, content string) (*model.Comment, error) {
	var comment model.Comment
	err := r.db.GetContext(ctx, &comment, `
		UPDATE comments SET content = $2, content_html = $2, updated_at = NOW()
		WHERE id = $1 AND deleted_at IS NULL
		RETURNING id, post_id, author_id, parent_id, root_id, depth, content, content_html,
		          media_url, is_pinned, is_spam, created_at, updated_at
	`, id, content)
	if err != nil {
		return nil, fmt.Errorf("update comment: %w", err)
	}
	return &comment, nil
}

func (r *CommentRepo) Delete(ctx context.Context, id string) error {
	_, err := r.db.ExecContext(ctx,
		`UPDATE comments SET deleted_at = NOW() WHERE id = $1`, id)
	return err
}

func (r *CommentRepo) Pin(ctx context.Context, id string, pin bool) error {
	_, err := r.db.ExecContext(ctx,
		`UPDATE comments SET is_pinned = $2 WHERE id = $1`, id, pin)
	return err
}

func (r *CommentRepo) ToggleReaction(ctx context.Context, userID, targetType, targetID, reaction string) (int, error) {
	var existingID string
	err := r.db.GetContext(ctx, &existingID, `
		SELECT id FROM reactions
		WHERE user_id = $1 AND target_type = $2 AND target_id = $3 AND reaction = $4
	`, userID, targetType, targetID, reaction)

	if err != nil && !errors.Is(err, sql.ErrNoRows) {
		return 0, fmt.Errorf("check reaction: %w", err)
	}

	if existingID != "" {
		_, err = r.db.ExecContext(ctx, `DELETE FROM reactions WHERE id = $1`, existingID)
		if err != nil {
			return 0, fmt.Errorf("delete reaction: %w", err)
		}
	} else {
		_, err = r.db.ExecContext(ctx, `
			INSERT INTO reactions (user_id, target_type, target_id, reaction)
			VALUES ($1, $2, $3, $4)
		`, userID, targetType, targetID, reaction)
		if err != nil {
			return 0, fmt.Errorf("insert reaction: %w", err)
		}
	}

	var count int
	r.db.GetContext(ctx, &count, `
		SELECT COUNT(*) FROM reactions
		WHERE target_type = $1 AND target_id = $2 AND reaction = $3
	`, targetType, targetID, reaction)

	return count, nil
}
