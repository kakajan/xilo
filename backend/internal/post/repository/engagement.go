package repository

import (
	"context"
	"fmt"
	"time"

	"github.com/lib/pq"
	authmodel "github.com/xilo-platform/xilo/internal/auth/model"
	"github.com/xilo-platform/xilo/internal/post/model"
	userutil "github.com/xilo-platform/xilo/internal/user/util"
)

func (r *PostRepo) EnrichPosts(ctx context.Context, posts []*model.Post, viewerID string) error {
	if len(posts) == 0 {
		return nil
	}

	postIDs := make([]string, len(posts))
	for i, p := range posts {
		postIDs[i] = p.ID
	}

	type countRow struct {
		PostID string `db:"post_id"`
		Count  int    `db:"count"`
	}
	var commentCounts []countRow
	err := r.db.SelectContext(ctx, &commentCounts, `
		SELECT post_id, COUNT(*)::int as count
		FROM comments
		WHERE post_id = ANY($1) AND deleted_at IS NULL
		GROUP BY post_id
	`, pq.Array(postIDs))
	if err != nil {
		return fmt.Errorf("comment counts: %w", err)
	}
	commentMap := make(map[string]int, len(commentCounts))
	for _, row := range commentCounts {
		commentMap[row.PostID] = row.Count
	}

	type reactionRow struct {
		TargetID string `db:"target_id"`
		Reaction string `db:"reaction"`
		Count    int    `db:"count"`
	}
	var reactionCounts []reactionRow
	err = r.db.SelectContext(ctx, &reactionCounts, `
		SELECT target_id, reaction, COUNT(*)::int as count
		FROM reactions
		WHERE target_type = 'post' AND target_id = ANY($1)
		GROUP BY target_id, reaction
	`, pq.Array(postIDs))
	if err != nil {
		return fmt.Errorf("reaction counts: %w", err)
	}
	reactionMap := make(map[string]map[string]int)
	for _, row := range reactionCounts {
		if reactionMap[row.TargetID] == nil {
			reactionMap[row.TargetID] = make(map[string]int)
		}
		reactionMap[row.TargetID][row.Reaction] = row.Count
	}

	var repostCounts []countRow
	err = r.db.SelectContext(ctx, &repostCounts, `
		SELECT post_id, COUNT(*)::int as count
		FROM reposts
		WHERE post_id = ANY($1)
		GROUP BY post_id
	`, pq.Array(postIDs))
	if err != nil {
		return fmt.Errorf("repost counts: %w", err)
	}
	repostMap := make(map[string]int, len(repostCounts))
	for _, row := range repostCounts {
		repostMap[row.PostID] = row.Count
	}

	viewerReactionMap := make(map[string][]string)
	bookmarkMap := make(map[string]bool)
	repostedMap := make(map[string]bool)
	if viewerID != "" {
		type viewerRow struct {
			TargetID string `db:"target_id"`
			Reaction string `db:"reaction"`
		}
		var viewerRows []viewerRow
		_ = r.db.SelectContext(ctx, &viewerRows, `
			SELECT target_id, reaction FROM reactions
			WHERE target_type = 'post' AND user_id = $1 AND target_id = ANY($2)
		`, viewerID, pq.Array(postIDs))
		for _, row := range viewerRows {
			viewerReactionMap[row.TargetID] = append(viewerReactionMap[row.TargetID], row.Reaction)
		}

		var bookmarked []string
		_ = r.db.SelectContext(ctx, &bookmarked, `
			SELECT post_id FROM bookmarks WHERE user_id = $1 AND post_id = ANY($2)
		`, viewerID, pq.Array(postIDs))
		for _, id := range bookmarked {
			bookmarkMap[id] = true
		}

		var reposted []string
		_ = r.db.SelectContext(ctx, &reposted, `
			SELECT post_id FROM reposts WHERE user_id = $1 AND post_id = ANY($2)
		`, viewerID, pq.Array(postIDs))
		for _, id := range reposted {
			repostedMap[id] = true
		}
	}

	for _, post := range posts {
		post.CommentCount = commentMap[post.ID]
		post.RepostCount = repostMap[post.ID]
		if reactions, ok := reactionMap[post.ID]; ok {
			post.Reactions = reactions
		} else {
			post.Reactions = map[string]int{}
		}
		if viewerID != "" {
			post.ViewerReactions = viewerReactionMap[post.ID]
			post.IsBookmarked = bookmarkMap[post.ID]
			post.IsReposted = repostedMap[post.ID]
		}
	}

	if err := r.EnrichQuotedPosts(ctx, posts); err != nil {
		return err
	}

	return nil
}

// EnrichQuotedPosts attaches one-level quoted_post summaries (no nested quotes).
func (r *PostRepo) EnrichQuotedPosts(ctx context.Context, posts []*model.Post) error {
	if len(posts) == 0 {
		return nil
	}
	ids := make([]string, 0)
	seen := make(map[string]struct{})
	for _, p := range posts {
		if p.QuotedPostID == nil || *p.QuotedPostID == "" {
			continue
		}
		id := *p.QuotedPostID
		if _, ok := seen[id]; ok {
			continue
		}
		seen[id] = struct{}{}
		ids = append(ids, id)
	}
	if len(ids) == 0 {
		return nil
	}

	type quoteRow struct {
		ID            string     `db:"id"`
		Title         string     `db:"title"`
		Slug          string     `db:"slug"`
		Excerpt       string     `db:"excerpt"`
		CoverImageURL *string    `db:"cover_image_url"`
		PublishedAt   *time.Time `db:"published_at"`
		AuthorID      string     `db:"author_id"`
		Username      string     `db:"username"`
		DisplayName   string     `db:"display_name"`
		AvatarURL     string     `db:"avatar_url"`
		Role          string     `db:"role"`
	}

	var rows []quoteRow
	err := r.db.SelectContext(ctx, &rows, `
		SELECT p.id, p.title, p.slug, COALESCE(p.excerpt, '') AS excerpt,
		       p.cover_image_url, p.published_at, p.author_id,
		       u.username,
		       COALESCE(u.display_name, '') AS display_name,
		       COALESCE(u.avatar_url, '') AS avatar_url,
		       COALESCE(u.role, '') AS role
		FROM posts p
		JOIN users u ON u.id = p.author_id
		WHERE p.id = ANY($1) AND p.deleted_at IS NULL
	`, pq.Array(ids))
	if err != nil {
		return fmt.Errorf("quoted posts: %w", err)
	}

	byID := make(map[string]*model.QuotedPostSummary, len(rows))
	for i := range rows {
		row := rows[i]
		author := &authmodel.User{
			ID:          row.AuthorID,
			Username:    row.Username,
			DisplayName: row.DisplayName,
			AvatarURL:   row.AvatarURL,
			Role:        row.Role,
		}
		author.IsVerified = userutil.IsVerifiedWriter(row.Role)
		byID[row.ID] = &model.QuotedPostSummary{
			ID:            row.ID,
			Title:         row.Title,
			Slug:          row.Slug,
			Excerpt:       row.Excerpt,
			CoverImageURL: row.CoverImageURL,
			Author:        author,
			PublishedAt:   row.PublishedAt,
		}
	}

	for _, p := range posts {
		if p.QuotedPostID == nil {
			continue
		}
		if q, ok := byID[*p.QuotedPostID]; ok {
			p.QuotedPost = q
		}
	}
	return nil
}
