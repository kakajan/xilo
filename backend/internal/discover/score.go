package discover

import (
	"math"
	"strings"
	"time"
)

const (
	// recencyLambda matches the discover-spec recency decay (hours).
	recencyLambda = 0.08
	// interestBoost elevates category/tag matches above similar non-matches.
	interestBoost = 1.0
	// engagementSoftener soft-normalizes likes+replies into roughly [0,1].
	engagementSoftener = 10.0
)

// ScoreInput holds the fields used by the simplified discover ranking helper.
type ScoreInput struct {
	CreatedAt     time.Time
	LikesCount    int
	RepliesCount  int
	PostCategory  string
	PostTags      []string
	InterestSlugs []string
	Now           time.Time
}

// MatchesInterest reports whether the post category or any tag matches an
// interest slug (case-insensitive).
func MatchesInterest(category string, tags []string, interestSlugs []string) bool {
	if len(interestSlugs) == 0 {
		return false
	}
	cat := strings.ToLower(strings.TrimSpace(category))
	tagSet := make(map[string]struct{}, len(tags))
	for _, t := range tags {
		tagSet[strings.ToLower(strings.TrimSpace(t))] = struct{}{}
	}
	for _, slug := range interestSlugs {
		s := strings.ToLower(strings.TrimSpace(slug))
		if s == "" {
			continue
		}
		if cat == s {
			return true
		}
		if _, ok := tagSet[s]; ok {
			return true
		}
	}
	return false
}

// ScoreComment returns a simplified discover score:
// recency + engagement + interest boost (when category/tags match).
// Anonymous / no-interest callers should pass a nil/empty InterestSlugs slice.
func ScoreComment(in ScoreInput) float64 {
	now := in.Now
	if now.IsZero() {
		now = time.Now().UTC()
	}
	created := in.CreatedAt
	if created.IsZero() {
		created = now
	}

	hours := now.Sub(created).Hours()
	if hours < 0 {
		hours = 0
	}
	recency := math.Exp(-recencyLambda * hours)

	rawEngagement := float64(in.LikesCount) + 2*float64(in.RepliesCount)
	if rawEngagement < 0 {
		rawEngagement = 0
	}
	engagement := rawEngagement / (rawEngagement + engagementSoftener)

	boost := 0.0
	if MatchesInterest(in.PostCategory, in.PostTags, in.InterestSlugs) {
		boost = interestBoost
	}

	return recency + engagement + boost
}
