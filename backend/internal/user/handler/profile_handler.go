package handler

import (
	"context"
	"encoding/json"
	"strconv"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/jmoiron/sqlx"
	"github.com/lib/pq"
	authmodel "github.com/xilo-platform/xilo/internal/auth/model"
	commentrepo "github.com/xilo-platform/xilo/internal/comment/repository"
	postmodel "github.com/xilo-platform/xilo/internal/post/model"
	postrepo "github.com/xilo-platform/xilo/internal/post/repository"
	userutil "github.com/xilo-platform/xilo/internal/user/util"
	pkgredis "github.com/xilo-platform/xilo/pkg/redis"
)

type ProfileHandler struct {
	db          *sqlx.DB
	rdb         *pkgredis.Client
	postRepo    *postrepo.PostRepo
	commentRepo *commentrepo.CommentRepo
}

func NewProfileHandler(db *sqlx.DB, rdb *pkgredis.Client, postRepo *postrepo.PostRepo, commentRepo *commentrepo.CommentRepo) *ProfileHandler {
	return &ProfileHandler{db: db, rdb: rdb, postRepo: postRepo, commentRepo: commentRepo}
}

type profileStats struct {
	Posts     int `json:"posts"`
	Followers int `json:"followers"`
	Following int `json:"following"`
}

type publicProfileResponse struct {
	ID          string       `json:"id"`
	Username    string       `json:"username"`
	DisplayName string       `json:"display_name"`
	AvatarURL   string       `json:"avatar_url"`
	Bio         string       `json:"bio"`
	IsVerified  bool         `json:"is_verified"`
	CreatedAt   time.Time    `json:"created_at"`
	Stats       profileStats `json:"stats"`
	IsFollowing bool         `json:"is_following"`
}

// GetPublicProfile returns a public user profile with stats.
func (h *ProfileHandler) GetPublicProfile(c *fiber.Ctx) error {
	username := c.Params("username")
	ctx := c.UserContext()

	cacheKey := "user:profile:" + username
	if h.rdb != nil {
		if cached, err := h.rdb.Get(ctx, cacheKey).Result(); err == nil && cached != "" {
			var resp publicProfileResponse
			if json.Unmarshal([]byte(cached), &resp) == nil {
				if viewerID, ok := c.Locals("userID").(string); ok && viewerID != "" {
					resp.IsFollowing = h.checkFollowing(viewerID, resp.ID)
				}
				return c.JSON(resp)
			}
		}
	}

	var user struct {
		ID          string    `db:"id"`
		Username    string    `db:"username"`
		DisplayName string    `db:"display_name"`
		AvatarURL   string    `db:"avatar_url"`
		Bio         string    `db:"bio"`
		Role        string    `db:"role"`
		CreatedAt   time.Time `db:"created_at"`
	}
	err := h.db.Get(&user, `
		SELECT id, username, display_name,
		       COALESCE(avatar_url, '') as avatar_url,
		       COALESCE(bio, '') as bio,
		       role, created_at
		FROM users
		WHERE username = $1 AND deleted_at IS NULL
	`, username)
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "user not found"})
	}

	var stats profileStats
	_ = h.db.Get(&stats.Posts, `
		SELECT COUNT(*) FROM posts
		WHERE author_id = $1 AND status = 'published' AND deleted_at IS NULL
	`, user.ID)
	_ = h.db.Get(&stats.Followers, `
		SELECT COUNT(*) FROM follows WHERE following_id = $1
	`, user.ID)
	_ = h.db.Get(&stats.Following, `
		SELECT COUNT(*) FROM follows WHERE follower_id = $1
	`, user.ID)

	resp := publicProfileResponse{
		ID:          user.ID,
		Username:    user.Username,
		DisplayName: user.DisplayName,
		AvatarURL:   user.AvatarURL,
		Bio:         user.Bio,
		IsVerified:  userutil.IsVerifiedWriter(user.Role),
		CreatedAt:   user.CreatedAt,
		Stats:       stats,
	}

	if viewerID, ok := c.Locals("userID").(string); ok && viewerID != "" {
		resp.IsFollowing = h.checkFollowing(viewerID, user.ID)
	}

	if h.rdb != nil {
		if data, err := json.Marshal(resp); err == nil {
			_ = h.rdb.Set(ctx, cacheKey, string(data), 10*time.Minute).Err()
		}
	}

	return c.JSON(resp)
}

func (h *ProfileHandler) checkFollowing(followerID, followingID string) bool {
	var exists bool
	_ = h.db.Get(&exists, `
		SELECT EXISTS(
			SELECT 1 FROM follows WHERE follower_id = $1 AND following_id = $2
		)
	`, followerID, followingID)
	return exists
}

// ListUserPosts lists posts for a user profile tab (posts, media, or archived).
func (h *ProfileHandler) ListUserPosts(c *fiber.Ctx) error {
	username := c.Params("username")
	tab := c.Query("tab", "posts")
	cursor := c.Query("cursor")
	limit, _ := strconv.Atoi(c.Query("limit", "20"))
	viewerID, _ := c.Locals("userID").(string)

	status := "published"
	mediaOnly := tab == "media"
	if tab == "archived" {
		var authorID string
		err := h.db.Get(&authorID, `SELECT id FROM users WHERE username = $1 AND deleted_at IS NULL`, username)
		if err != nil {
			return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "user not found"})
		}
		if viewerID == "" || viewerID != authorID {
			return c.Status(fiber.StatusForbidden).JSON(fiber.Map{"error": "archived posts are private"})
		}
		status = "archived"
		mediaOnly = false
	}

	posts, nextCursor, err := h.postRepo.List(c.UserContext(), postmodel.PostListParams{
		Cursor:    cursor,
		Limit:     limit,
		Author:    username,
		Status:    status,
		MediaOnly: mediaOnly,
		ViewerID:  viewerID,
	})
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed to list posts"})
	}

	return c.JSON(fiber.Map{
		"data":        posts,
		"next_cursor": nextCursor,
		"has_more":    nextCursor != "",
	})
}

// ListUserReplies lists comments authored by the user.
func (h *ProfileHandler) ListUserReplies(c *fiber.Ctx) error {
	username := c.Params("username")
	cursor := c.Query("cursor")
	limit, _ := strconv.Atoi(c.Query("limit", "20"))

	comments, nextCursor, err := h.commentRepo.ListByAuthor(c.UserContext(), username, cursor, limit)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed to list replies"})
	}

	return c.JSON(fiber.Map{
		"data":        comments,
		"next_cursor": nextCursor,
		"has_more":    nextCursor != "",
	})
}

// ListUserLikes lists posts the user has reacted to.
func (h *ProfileHandler) ListUserLikes(c *fiber.Ctx) error {
	username := c.Params("username")
	cursor := c.Query("cursor")
	limit, _ := strconv.Atoi(c.Query("limit", "20"))
	if limit <= 0 || limit > 50 {
		limit = 20
	}
	viewerID, _ := c.Locals("userID").(string)
	ctx := c.UserContext()

	var userID string
	err := h.db.Get(&userID, `SELECT id FROM users WHERE username = $1 AND deleted_at IS NULL`, username)
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "user not found"})
	}

	query := `
		SELECT p.id, p.author_id, p.title, p.slug, p.excerpt, p.cover_image_url,
		       p.category, p.tags, p.status, p.is_premium,
		       p.word_count, p.reading_time, p.language, p.view_count, p.published_at,
		       p.created_at, p.updated_at, r.id as reaction_id
		FROM reactions r
		JOIN posts p ON p.id = r.target_id
		WHERE r.user_id = $1 AND r.target_type = 'post'
		  AND p.status = 'published' AND p.deleted_at IS NULL
	`
	args := []interface{}{userID}
	argIdx := 2
	if cursor != "" {
		query += ` AND r.created_at < (SELECT created_at FROM reactions WHERE id = $` + strconv.Itoa(argIdx) + `)`
		args = append(args, cursor)
		argIdx++
	}
	query += ` ORDER BY r.created_at DESC LIMIT $` + strconv.Itoa(argIdx)
	args = append(args, limit+1)

	type likedRow struct {
		postmodel.Post
		ReactionID string `db:"reaction_id"`
	}
	var rows []likedRow
	if err := h.db.SelectContext(ctx, &rows, query, args...); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed to list likes"})
	}

	var nextCursor string
	posts := make([]*postmodel.Post, 0, len(rows))
	for _, row := range rows {
		p := row.Post
		posts = append(posts, &p)
	}
	if len(posts) > limit {
		posts = posts[:limit]
		nextCursor = rows[limit-1].ReactionID
	}

	// Load authors
	authorIDs := make(map[string]bool)
	for _, p := range posts {
		authorIDs[p.AuthorID] = true
	}
	if len(authorIDs) > 0 {
		ids := make([]string, 0, len(authorIDs))
		for id := range authorIDs {
			ids = append(ids, id)
		}
		var users []authmodel.User
		_ = h.db.SelectContext(ctx, &users, `
			SELECT id, username, display_name,
			       COALESCE(avatar_url, '') as avatar_url,
			       COALESCE(bio, '') as bio, role
			FROM users WHERE id = ANY($1)
		`, pq.Array(ids))
		authorMap := make(map[string]*authmodel.User)
		for i := range users {
			users[i].IsVerified = userutil.IsVerifiedWriter(users[i].Role)
			authorMap[users[i].ID] = &users[i]
		}
		for _, p := range posts {
			if a, ok := authorMap[p.AuthorID]; ok {
				p.Author = a
			}
		}
	}

	_ = h.postRepo.EnrichPosts(ctx, posts, viewerID)

	return c.JSON(fiber.Map{
		"data":        posts,
		"next_cursor": nextCursor,
		"has_more":    nextCursor != "",
	})
}

type followListUser struct {
	ID          string `json:"id" db:"id"`
	Username    string `json:"username" db:"username"`
	DisplayName string `json:"display_name" db:"display_name"`
	AvatarURL   string `json:"avatar_url" db:"avatar_url"`
	Role        string `json:"-" db:"role"`
	CreatedAt   time.Time `json:"-" db:"created_at"`
	IsVerified  bool   `json:"is_verified"`
	IsFollowing bool   `json:"is_following"`
}

// ListUserFollowers lists users who follow the given username.
func (h *ProfileHandler) ListUserFollowers(c *fiber.Ctx) error {
	return h.listFollowGraph(c, followGraphFollowers)
}

// ListUserFollowing lists users that the given username follows.
func (h *ProfileHandler) ListUserFollowing(c *fiber.Ctx) error {
	return h.listFollowGraph(c, followGraphFollowing)
}

type followGraphKind int

const (
	followGraphFollowers followGraphKind = iota
	followGraphFollowing
)

func (h *ProfileHandler) listFollowGraph(c *fiber.Ctx, kind followGraphKind) error {
	username := c.Params("username")
	cursor := c.Query("cursor")
	limit, _ := strconv.Atoi(c.Query("limit", "20"))
	if limit <= 0 || limit > 50 {
		limit = 20
	}
	viewerID, _ := c.Locals("userID").(string)
	ctx := c.UserContext()

	var targetID string
	err := h.db.GetContext(ctx, &targetID, `
		SELECT id FROM users WHERE username = $1 AND deleted_at IS NULL
	`, username)
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{"error": "user not found"})
	}

	var (
		joinOn        string
		whereCol      string
		whereCol2     string
		edgeUserCol   string // listed user id on follows row
		edgeUserCol2  string // same column with f2 alias for cursor subquery
	)
	switch kind {
	case followGraphFollowers:
		// Users who follow target: f.following_id = target, join follower
		joinOn = "u.id = f.follower_id"
		whereCol = "f.following_id"
		whereCol2 = "f2.following_id"
		edgeUserCol = "f.follower_id"
		edgeUserCol2 = "f2.follower_id"
	default:
		// Users target follows: f.follower_id = target, join following
		joinOn = "u.id = f.following_id"
		whereCol = "f.follower_id"
		whereCol2 = "f2.follower_id"
		edgeUserCol = "f.following_id"
		edgeUserCol2 = "f2.following_id"
	}

	query := `
		SELECT u.id, u.username, u.display_name,
		       COALESCE(u.avatar_url, '') AS avatar_url,
		       u.role, f.created_at
		FROM follows f
		JOIN users u ON ` + joinOn + `
		WHERE ` + whereCol + ` = $1 AND u.deleted_at IS NULL
	`
	args := []interface{}{targetID}
	argIdx := 2
	if cursor != "" {
		query += ` AND (f.created_at, ` + edgeUserCol + `) < (
			SELECT f2.created_at, ` + edgeUserCol2 + `
			FROM follows f2
			WHERE ` + edgeUserCol2 + ` = $` + strconv.Itoa(argIdx) + `
			  AND ` + whereCol2 + ` = $1
		)`
		args = append(args, cursor)
		argIdx++
	}
	query += ` ORDER BY f.created_at DESC, ` + edgeUserCol + ` DESC LIMIT $` + strconv.Itoa(argIdx)
	args = append(args, limit+1)

	rows := make([]followListUser, 0)
	if err := h.db.SelectContext(ctx, &rows, query, args...); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{"error": "failed to list follows"})
	}

	var nextCursor string
	if len(rows) > limit {
		rows = rows[:limit]
		nextCursor = rows[limit-1].ID
	}

	ids := make([]string, 0, len(rows))
	for i := range rows {
		rows[i].IsVerified = userutil.IsVerifiedWriter(rows[i].Role)
		ids = append(ids, rows[i].ID)
	}

	followingSet := map[string]bool{}
	if viewerID != "" && len(ids) > 0 {
		var followedIDs []string
		_ = h.db.SelectContext(ctx, &followedIDs, `
			SELECT following_id FROM follows
			WHERE follower_id = $1 AND following_id = ANY($2)
		`, viewerID, pq.Array(ids))
		for _, id := range followedIDs {
			followingSet[id] = true
		}
	}
	for i := range rows {
		rows[i].IsFollowing = followingSet[rows[i].ID]
	}

	return c.JSON(fiber.Map{
		"data":        rows,
		"next_cursor": nextCursor,
		"has_more":    nextCursor != "",
	})
}

// InvalidateProfileCache removes cached profile for a username.
func (h *ProfileHandler) InvalidateProfileCache(ctx context.Context, username string) {
	if h.rdb != nil {
		_ = h.rdb.Del(ctx, "user:profile:"+username).Err()
	}
}
