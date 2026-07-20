package discover

import (
	"context"
	"fmt"
	"sort"
	"strings"
	"time"

	"github.com/jmoiron/sqlx"
)

const (
	defaultLimit     = 50
	maxLimit         = 100
	candidatePoolCap = 500
)

// Repository loads Discover comment candidates and viewer interests.
type Repository struct {
	db *sqlx.DB
}

func NewRepository(db *sqlx.DB) *Repository {
	return &Repository{db: db}
}

// UserInterestSlugs returns active interest slugs for the viewer.
// Safe when interests / user_interests tables exist (migration 000020).
func (r *Repository) UserInterestSlugs(ctx context.Context, userID string) ([]string, error) {
	if userID == "" {
		return nil, nil
	}
	var slugs []string
	err := r.db.SelectContext(ctx, &slugs, `
		SELECT i.slug
		FROM user_interests ui
		JOIN interests i ON i.id = ui.interest_id
		WHERE ui.user_id = $1 AND i.is_active = TRUE
		ORDER BY i.sort_order, i.slug
	`, userID)
	if err != nil {
		return nil, fmt.Errorf("load user interests: %w", err)
	}
	return slugs, nil
}

// ListComments returns Discover comments ranked by simplified interest-aware score.
// interestFilter, when non-empty, restricts to posts matching that slug (category or tags).
func (r *Repository) ListComments(ctx context.Context, userID, interestFilter string, limit int) ([]Comment, error) {
	limit = clampLimit(limit)
	pool := limit * 5
	if pool < 100 {
		pool = 100
	}
	if pool > candidatePoolCap {
		pool = candidatePoolCap
	}

	interestFilter = strings.ToLower(strings.TrimSpace(interestFilter))

	slugs, err := r.UserInterestSlugs(ctx, userID)
	if err != nil {
		return nil, err
	}

	rows, err := r.fetchCandidates(ctx, interestFilter, pool)
	if err != nil {
		return nil, err
	}

	now := time.Now().UTC()
	type scored struct {
		comment Comment
		score   float64
	}
	scoredRows := make([]scored, 0, len(rows))
	for _, row := range rows {
		tags := []string(row.PostTags)
		if tags == nil {
			tags = []string{}
		}
		c := Comment{
			ID:         row.ID,
			PostID:     row.PostID,
			AuthorID:   row.AuthorID,
			Content:    row.Content,
			CreatedAt:  row.CreatedAt,
			LikeCount:  row.LikesCount,
			ReplyCount: row.RepliesCount,
			Author: Author{
				ID:          row.AuthorID,
				Username:    row.Username,
				DisplayName: row.DisplayName,
				AvatarURL:   row.AvatarURL,
			},
			Post: PostContext{
				ID:             row.PostID,
				Title:          row.PostTitle,
				Slug:           row.PostSlug,
				AuthorUsername: row.PostAuthorUsername,
				Category:       row.PostCategory,
				Tags:           tags,
			},
		}
		scoredRows = append(scoredRows, scored{
			comment: c,
			score: ScoreComment(ScoreInput{
				CreatedAt:     row.CreatedAt,
				LikesCount:    row.LikesCount,
				RepliesCount:  row.RepliesCount,
				PostCategory:  row.PostCategory,
				PostTags:      tags,
				InterestSlugs: slugs,
				Now:           now,
			}),
		})
	}

	sort.SliceStable(scoredRows, func(i, j int) bool {
		if scoredRows[i].score != scoredRows[j].score {
			return scoredRows[i].score > scoredRows[j].score
		}
		return scoredRows[i].comment.CreatedAt.After(scoredRows[j].comment.CreatedAt)
	})

	if len(scoredRows) > limit {
		scoredRows = scoredRows[:limit]
	}

	out := make([]Comment, len(scoredRows))
	for i, s := range scoredRows {
		out[i] = s.comment
	}
	return out, nil
}

func (r *Repository) fetchCandidates(ctx context.Context, interestFilter string, pool int) ([]commentRow, error) {
	query := `
		SELECT
			c.id,
			c.content,
			c.created_at,
			COALESCE((
				SELECT COUNT(*)::int
				FROM reactions rx
				WHERE rx.target_type = 'comment'
				  AND rx.target_id = c.id
				  AND rx.reaction IN ('like', 'heart')
			), 0) AS likes_count,
			COALESCE((
				SELECT COUNT(*)::int
				FROM comments r
				WHERE r.parent_id = c.id AND r.deleted_at IS NULL
			), 0) AS replies_count,
			u.id AS author_id,
			u.username,
			COALESCE(NULLIF(u.display_name, ''), u.username) AS display_name,
			COALESCE(u.avatar_url, '') AS avatar_url,
			p.id AS post_id,
			p.title AS post_title,
			p.slug AS post_slug,
			pu.username AS post_author_username,
			COALESCE(p.category, '') AS post_category,
			COALESCE(p.tags, '{}') AS post_tags
		FROM comments c
		JOIN users u ON u.id = c.author_id AND u.deleted_at IS NULL
		JOIN posts p ON p.id = c.post_id AND p.deleted_at IS NULL AND p.status = 'published'
		JOIN users pu ON pu.id = p.author_id AND pu.deleted_at IS NULL
		WHERE c.deleted_at IS NULL
		  AND c.is_spam = FALSE
	`
	args := []interface{}{}
	argIdx := 1

	if interestFilter != "" {
		query += fmt.Sprintf(`
		  AND (
		    lower(COALESCE(p.category, '')) = $%d
		    OR EXISTS (
		      SELECT 1
		      FROM unnest(COALESCE(p.tags, '{}'::text[])) AS t(tag)
		      WHERE lower(t.tag) = $%d
		    )
		  )
		`, argIdx, argIdx)
		args = append(args, interestFilter)
		argIdx++
	}

	query += fmt.Sprintf(`
		ORDER BY c.created_at DESC
		LIMIT $%d
	`, argIdx)
	args = append(args, pool)

	var rows []commentRow
	if err := r.db.SelectContext(ctx, &rows, query, args...); err != nil {
		return nil, fmt.Errorf("list discover comments: %w", err)
	}
	return rows, nil
}

func clampLimit(limit int) int {
	if limit <= 0 {
		return defaultLimit
	}
	if limit > maxLimit {
		return maxLimit
	}
	return limit
}
