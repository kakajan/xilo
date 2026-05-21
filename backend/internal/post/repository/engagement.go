package repository

import (
	"context"
	"fmt"

	"github.com/lib/pq"
	"github.com/xilo-platform/xilo/internal/post/model"
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

	viewerReactionMap := make(map[string][]string)
	bookmarkMap := make(map[string]bool)
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
	}

	for _, post := range posts {
		post.CommentCount = commentMap[post.ID]
		if reactions, ok := reactionMap[post.ID]; ok {
			post.Reactions = reactions
		} else {
			post.Reactions = map[string]int{}
		}
		if viewerID != "" {
			post.ViewerReactions = viewerReactionMap[post.ID]
			post.IsBookmarked = bookmarkMap[post.ID]
		}
	}

	return nil
}
