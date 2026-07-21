package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"time"

	"github.com/jmoiron/sqlx"
	authmodel "github.com/xilo-platform/xilo/internal/auth/model"
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
	var row struct {
		ID           string    `db:"id"`
		PostID       string    `db:"post_id"`
		AuthorID     string    `db:"author_id"`
		ParentID     *string   `db:"parent_id"`
		RootID       *string   `db:"root_id"`
		Depth        int       `db:"depth"`
		Content      string    `db:"content"`
		ContentHTML  string    `db:"content_html"`
		MediaURL     string    `db:"media_url"`
		IsPinned     bool      `db:"is_pinned"`
		IsSpam       bool      `db:"is_spam"`
		CreatedAt    time.Time `db:"created_at"`
		UpdatedAt    time.Time `db:"updated_at"`
		UserID       string    `db:"user_id"`
		Username     string    `db:"username"`
		DisplayName  string    `db:"display_name"`
		AvatarURL    string    `db:"avatar_url"`
	}

	err := r.db.GetContext(ctx, &row, `
		SELECT c.id, c.post_id, c.author_id, c.parent_id, c.root_id, c.depth,
		       c.content,
		       COALESCE(c.content_html, '') AS content_html,
		       COALESCE(c.media_url, '') AS media_url,
		       c.is_pinned, c.is_spam,
		       c.created_at, c.updated_at,
		       u.id AS user_id, u.username,
		       COALESCE(u.display_name, '') AS display_name,
		       COALESCE(u.avatar_url, '') AS avatar_url
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

	comment := &model.Comment{
		ID:          row.ID,
		PostID:      row.PostID,
		AuthorID:    row.AuthorID,
		ParentID:    row.ParentID,
		RootID:      row.RootID,
		Depth:       row.Depth,
		Content:     row.Content,
		ContentHTML: row.ContentHTML,
		MediaURL:    row.MediaURL,
		IsPinned:    row.IsPinned,
		IsSpam:      row.IsSpam,
		CreatedAt:   row.CreatedAt,
		UpdatedAt:   row.UpdatedAt,
		Author: &authmodel.User{
			ID:          row.UserID,
			Username:    row.Username,
			DisplayName: row.DisplayName,
			AvatarURL:   row.AvatarURL,
		},
	}
	return comment, nil
}

func (r *CommentRepo) ListByPost(ctx context.Context, postID string, cursor string, limit int, sort string, viewerID string) ([]*model.Comment, string, error) {
	if limit <= 0 || limit > 50 {
		limit = 20
	}

	allComments, err := r.getAllComments(ctx, postID)
	if err != nil {
		return nil, "", err
	}

	flatIDs := make([]string, len(allComments))
	for i, c := range allComments {
		flatIDs[i] = c.ID
	}
	if err := r.attachReactionCounts(ctx, allComments, flatIDs); err != nil {
		return nil, "", err
	}
	if err := r.attachViewerReactions(ctx, allComments, flatIDs, viewerID); err != nil {
		return nil, "", err
	}
	if err := r.attachBookmarks(ctx, allComments, flatIDs, viewerID); err != nil {
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
	var rows []struct {
		ID           string    `db:"id"`
		PostID       string    `db:"post_id"`
		AuthorID     string    `db:"author_id"`
		ParentID     *string   `db:"parent_id"`
		RootID       *string   `db:"root_id"`
		Depth        int       `db:"depth"`
		Content      string    `db:"content"`
		ContentHTML  string    `db:"content_html"`
		MediaURL     string    `db:"media_url"`
		IsPinned     bool      `db:"is_pinned"`
		IsSpam       bool      `db:"is_spam"`
		CreatedAt    time.Time `db:"created_at"`
		UpdatedAt    time.Time `db:"updated_at"`
		UserID       string    `db:"user_id"`
		Username     string    `db:"username"`
		DisplayName  string    `db:"display_name"`
		AvatarURL    string    `db:"avatar_url"`
	}

	err := r.db.SelectContext(ctx, &rows, `
		SELECT c.id, c.post_id, c.author_id, c.parent_id, c.root_id, c.depth,
		       c.content,
		       COALESCE(c.content_html, '') AS content_html,
		       COALESCE(c.media_url, '') AS media_url,
		       c.is_pinned, c.is_spam,
		       c.created_at, c.updated_at,
		       u.id AS user_id, u.username,
		       COALESCE(u.display_name, '') AS display_name,
		       COALESCE(u.avatar_url, '') AS avatar_url
		FROM comments c
		JOIN users u ON c.author_id = u.id
		WHERE c.post_id = $1 AND c.deleted_at IS NULL
		ORDER BY c.created_at ASC
		LIMIT 500
	`, postID)
	if err != nil {
		return nil, fmt.Errorf("get all comments: %w", err)
	}

	comments := make([]*model.Comment, len(rows))
	for i, r := range rows {
		comments[i] = &model.Comment{
			ID:          r.ID,
			PostID:      r.PostID,
			AuthorID:    r.AuthorID,
			ParentID:    r.ParentID,
			RootID:      r.RootID,
			Depth:       r.Depth,
			Content:     r.Content,
			ContentHTML: r.ContentHTML,
			MediaURL:    r.MediaURL,
			IsPinned:    r.IsPinned,
			IsSpam:      r.IsSpam,
			CreatedAt:   r.CreatedAt,
			UpdatedAt:   r.UpdatedAt,
			Author: &authmodel.User{
				ID:          r.UserID,
				Username:    r.Username,
				DisplayName: r.DisplayName,
				AvatarURL:   r.AvatarURL,
			},
		}
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

// GetPostNotifyTarget returns the post author's user id, username, and slug for deep links.
func (r *CommentRepo) GetPostNotifyTarget(ctx context.Context, postID string) (authorID, username, slug string, err error) {
	var row struct {
		AuthorID string `db:"author_id"`
		Username string `db:"username"`
		Slug     string `db:"slug"`
	}
	err = r.db.GetContext(ctx, &row, `
		SELECT p.author_id, u.username, p.slug
		FROM posts p
		JOIN users u ON u.id = p.author_id
		WHERE p.id = $1 AND p.deleted_at IS NULL
	`, postID)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return "", "", "", fmt.Errorf("post not found")
		}
		return "", "", "", fmt.Errorf("get post notify target: %w", err)
	}
	return row.AuthorID, row.Username, row.Slug, nil
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
	// Treat like/heart as one family so clients cannot get stuck with a legacy heart row
	// while toggling "like" (or the reverse).
	family := reactionFamily(reaction)
	if len(family) > 0 {
		return r.toggleReactionFamily(ctx, userID, targetType, targetID, family, "like")
	}

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

func reactionFamily(reaction string) []string {
	switch reaction {
	case "like", "heart":
		return []string{"like", "heart"}
	default:
		return nil
	}
}

func (r *CommentRepo) toggleReactionFamily(
	ctx context.Context,
	userID, targetType, targetID string,
	family []string,
	insertAs string,
) (int, error) {
	var existingIDs []string
	query, args, err := sqlx.In(`
		SELECT id FROM reactions
		WHERE user_id = ? AND target_type = ? AND target_id = ? AND reaction IN (?)
	`, userID, targetType, targetID, family)
	if err != nil {
		return 0, fmt.Errorf("build reaction family query: %w", err)
	}
	query = r.db.Rebind(query)
	if err := r.db.SelectContext(ctx, &existingIDs, query, args...); err != nil {
		return 0, fmt.Errorf("check reaction family: %w", err)
	}

	if len(existingIDs) > 0 {
		delQuery, delArgs, err := sqlx.In(`DELETE FROM reactions WHERE id IN (?)`, existingIDs)
		if err != nil {
			return 0, fmt.Errorf("build delete reaction family query: %w", err)
		}
		delQuery = r.db.Rebind(delQuery)
		if _, err := r.db.ExecContext(ctx, delQuery, delArgs...); err != nil {
			return 0, fmt.Errorf("delete reaction family: %w", err)
		}
	} else {
		_, err := r.db.ExecContext(ctx, `
			INSERT INTO reactions (user_id, target_type, target_id, reaction)
			VALUES ($1, $2, $3, $4)
		`, userID, targetType, targetID, insertAs)
		if err != nil {
			return 0, fmt.Errorf("insert reaction: %w", err)
		}
	}

	countQuery, countArgs, err := sqlx.In(`
		SELECT COUNT(*) FROM reactions
		WHERE target_type = ? AND target_id = ? AND reaction IN (?)
	`, targetType, targetID, family)
	if err != nil {
		return 0, fmt.Errorf("build count reaction family query: %w", err)
	}
	countQuery = r.db.Rebind(countQuery)
	var count int
	if err := r.db.GetContext(ctx, &count, countQuery, countArgs...); err != nil {
		return 0, fmt.Errorf("count reaction family: %w", err)
	}
	return count, nil
}
