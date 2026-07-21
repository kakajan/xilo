package repository

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"log/slog"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/lib/pq"
	authmodel "github.com/xilo-platform/xilo/internal/auth/model"
	"github.com/xilo-platform/xilo/internal/comment/model"
	userutil "github.com/xilo-platform/xilo/internal/user/util"
)

type authorCommentRow struct {
	ID          string         `db:"id"`
	PostID      string         `db:"post_id"`
	AuthorID    string         `db:"author_id"`
	ParentID    sql.NullString `db:"parent_id"`
	RootID      sql.NullString `db:"root_id"`
	Depth       int            `db:"depth"`
	Content     string         `db:"content"`
	ContentHTML string         `db:"content_html"`
	MediaURL    string         `db:"media_url"`
	IsPinned    bool           `db:"is_pinned"`
	IsSpam      bool           `db:"is_spam"`
	CreatedAt   time.Time      `db:"created_at"`
	UpdatedAt   time.Time      `db:"updated_at"`
	Username    string         `db:"username"`
	DisplayName string         `db:"display_name"`
	AvatarURL   sql.NullString `db:"avatar_url"`
	Role        string         `db:"role"`
	PostTitle   string         `db:"post_title"`
	PostSlug    string         `db:"post_slug"`
}

// ListByAuthor returns flat comments by username for profile Replies tab.
func (r *CommentRepo) ListByAuthor(ctx context.Context, username, cursor string, limit int, viewerID string) ([]*model.Comment, string, error) {
	if limit <= 0 || limit > 50 {
		limit = 20
	}

	var authorID string
	err := r.db.GetContext(ctx, &authorID, `
		SELECT id FROM users WHERE username = $1 AND deleted_at IS NULL
	`, username)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, "", fmt.Errorf("author not found")
		}
		return nil, "", err
	}

	query := `
		SELECT c.id, c.post_id, c.author_id, c.parent_id, c.root_id, c.depth,
		       c.content,
		       COALESCE(c.content_html, '') AS content_html,
		       COALESCE(c.media_url, '') AS media_url,
		       c.is_pinned, c.is_spam, c.created_at, c.updated_at,
		       u.username,
		       COALESCE(u.display_name, '') AS display_name,
		       u.avatar_url, COALESCE(u.role, '') AS role,
		       p.title as post_title, p.slug as post_slug
		FROM comments c
		JOIN users u ON c.author_id = u.id
		JOIN posts p ON p.id = c.post_id
		WHERE c.author_id = $1 AND c.deleted_at IS NULL
		  AND p.status = 'published' AND p.deleted_at IS NULL
	`
	args := []interface{}{authorID}
	argIdx := 2
	if cursor != "" {
		query += ` AND c.created_at < (SELECT created_at FROM comments WHERE id = $` + strconv.Itoa(argIdx) + `)`
		args = append(args, cursor)
		argIdx++
	}
	query += ` ORDER BY c.created_at DESC LIMIT $` + strconv.Itoa(argIdx)
	args = append(args, limit+1)

	var rows []authorCommentRow
	if err := r.db.SelectContext(ctx, &rows, query, args...); err != nil {
		return nil, "", fmt.Errorf("list by author: %w", err)
	}

	var nextCursor string
	if len(rows) > limit {
		rows = rows[:limit]
		nextCursor = rows[len(rows)-1].ID
	}

	comments := make([]*model.Comment, len(rows))
	ids := make([]string, len(rows))
	for i, row := range rows {
		c := &model.Comment{
			ID:          row.ID,
			PostID:      row.PostID,
			AuthorID:    row.AuthorID,
			Depth:       row.Depth,
			Content:     row.Content,
			ContentHTML: row.ContentHTML,
			MediaURL:    row.MediaURL,
			IsPinned:    row.IsPinned,
			IsSpam:      row.IsSpam,
			CreatedAt:   row.CreatedAt,
			UpdatedAt:   row.UpdatedAt,
			Post: &model.PostRef{
				ID:    row.PostID,
				Title: row.PostTitle,
				Slug:  row.PostSlug,
			},
		}
		if row.ParentID.Valid {
			c.ParentID = &row.ParentID.String
		}
		if row.RootID.Valid {
			c.RootID = &row.RootID.String
		}
		author := &authmodel.User{
			ID:          row.AuthorID,
			Username:    row.Username,
			DisplayName: row.DisplayName,
			Role:        row.Role,
			IsVerified:  userutil.IsVerifiedWriter(row.Role),
		}
		if row.AvatarURL.Valid {
			author.AvatarURL = row.AvatarURL.String
		}
		c.Author = author
		comments[i] = c
		ids[i] = c.ID
	}

	if err := r.attachReactionCounts(ctx, comments, ids); err != nil {
		return nil, "", err
	}
	if err := r.attachViewerReactions(ctx, comments, ids, viewerID); err != nil {
		return nil, "", err
	}
	if err := r.attachBookmarks(ctx, comments, ids, viewerID); err != nil {
		return nil, "", err
	}
	if err := r.attachReposts(ctx, comments, ids, viewerID); err != nil {
		return nil, "", err
	}

	return comments, nextCursor, nil
}

func (r *CommentRepo) attachReposts(ctx context.Context, comments []*model.Comment, ids []string, viewerID string) error {
	if len(ids) == 0 {
		return nil
	}
	if viewerID == "" {
		return nil
	}
	var reposted []string
	err := r.db.SelectContext(ctx, &reposted, `
		SELECT comment_id FROM comment_reposts
		WHERE user_id = $1 AND comment_id = ANY($2)
	`, viewerID, pq.Array(ids))
	if err != nil {
		slog.Warn("attach comment reposts failed", "error", err)
		return nil
	}
	set := make(map[string]bool, len(reposted))
	for _, id := range reposted {
		set[id] = true
	}
	for _, c := range comments {
		c.IsReposted = set[c.ID]
	}
	return nil
}

func sortCommentRoots(roots []*model.Comment, sortMode string) {
	switch strings.ToLower(strings.TrimSpace(sortMode)) {
	case "oldest":
		sort.SliceStable(roots, func(i, j int) bool {
			if roots[i].IsPinned != roots[j].IsPinned {
				return roots[i].IsPinned
			}
			return roots[i].CreatedAt.Before(roots[j].CreatedAt)
		})
	case "most_reacted":
		sort.SliceStable(roots, func(i, j int) bool {
			if roots[i].IsPinned != roots[j].IsPinned {
				return roots[i].IsPinned
			}
			li, lj := reactionTotal(roots[i]), reactionTotal(roots[j])
			if li != lj {
				return li > lj
			}
			return roots[i].CreatedAt.After(roots[j].CreatedAt)
		})
	case "most_replied":
		sort.SliceStable(roots, func(i, j int) bool {
			if roots[i].IsPinned != roots[j].IsPinned {
				return roots[i].IsPinned
			}
			ri, rj := len(roots[i].Replies), len(roots[j].Replies)
			if ri != rj {
				return ri > rj
			}
			return roots[i].CreatedAt.After(roots[j].CreatedAt)
		})
	default: // newest
		sort.SliceStable(roots, func(i, j int) bool {
			if roots[i].IsPinned != roots[j].IsPinned {
				return roots[i].IsPinned
			}
			return roots[i].CreatedAt.After(roots[j].CreatedAt)
		})
	}
}

func reactionTotal(c *model.Comment) int {
	if c == nil || c.Reactions == nil {
		return 0
	}
	total := 0
	for _, n := range c.Reactions {
		total += n
	}
	return total
}

func (r *CommentRepo) attachReactionCounts(ctx context.Context, comments []*model.Comment, ids []string) error {
	if len(ids) == 0 {
		return nil
	}

	type reactionRow struct {
		TargetID string `db:"target_id"`
		Reaction string `db:"reaction"`
		Count    int    `db:"count"`
	}
	var rows []reactionRow
	err := r.db.SelectContext(ctx, &rows, `
		SELECT target_id, reaction, COUNT(*)::int as count
		FROM reactions
		WHERE target_type = 'comment' AND target_id = ANY($1)
		GROUP BY target_id, reaction
	`, pq.Array(ids))
	if err != nil {
		return err
	}

	reactionMap := make(map[string]map[string]int)
	for _, row := range rows {
		if reactionMap[row.TargetID] == nil {
			reactionMap[row.TargetID] = make(map[string]int)
		}
		reactionMap[row.TargetID][row.Reaction] = row.Count
	}

	for _, c := range comments {
		if reactions, ok := reactionMap[c.ID]; ok {
			c.Reactions = reactions
		} else {
			c.Reactions = map[string]int{}
		}
	}
	return nil
}

func (r *CommentRepo) attachBookmarks(ctx context.Context, comments []*model.Comment, ids []string, viewerID string) error {
	if len(ids) == 0 || viewerID == "" {
		return nil
	}

	var bookmarked []string
	err := r.db.SelectContext(ctx, &bookmarked, `
		SELECT comment_id FROM comment_bookmarks
		WHERE user_id = $1 AND comment_id = ANY($2)
	`, viewerID, pq.Array(ids))
	if err != nil {
		// Bookmarks are non-critical; missing table/migration must not 500 the comment list.
		slog.Warn("attach comment bookmarks failed", "error", err)
		return nil
	}

	bookmarkSet := make(map[string]bool, len(bookmarked))
	for _, id := range bookmarked {
		bookmarkSet[id] = true
	}
	for _, c := range comments {
		c.IsBookmarked = bookmarkSet[c.ID]
	}
	return nil
}

func (r *CommentRepo) attachViewerReactions(ctx context.Context, comments []*model.Comment, ids []string, viewerID string) error {
	if len(ids) == 0 || viewerID == "" {
		return nil
	}

	type viewerRow struct {
		TargetID string `db:"target_id"`
		Reaction string `db:"reaction"`
	}
	var rows []viewerRow
	err := r.db.SelectContext(ctx, &rows, `
		SELECT target_id, reaction FROM reactions
		WHERE target_type = 'comment' AND user_id = $1 AND target_id = ANY($2)
	`, viewerID, pq.Array(ids))
	if err != nil {
		return err
	}

	viewerMap := make(map[string][]string, len(rows))
	for _, row := range rows {
		viewerMap[row.TargetID] = append(viewerMap[row.TargetID], row.Reaction)
	}
	for _, c := range comments {
		if reactions, ok := viewerMap[c.ID]; ok {
			c.ViewerReactions = reactions
		}
	}
	return nil
}
