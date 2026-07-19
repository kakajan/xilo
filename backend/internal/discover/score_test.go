package discover

import (
	"testing"
	"time"
)

func TestMatchesInterest(t *testing.T) {
	tests := []struct {
		name     string
		category string
		tags     []string
		slugs    []string
		want     bool
	}{
		{
			name:     "category match case-insensitive",
			category: "Music",
			tags:     []string{"live"},
			slugs:    []string{"music"},
			want:     true,
		},
		{
			name:     "tag match",
			category: "news",
			tags:     []string{"Technology", "gadgets"},
			slugs:    []string{"technology"},
			want:     true,
		},
		{
			name:     "no match",
			category: "sports",
			tags:     []string{"football"},
			slugs:    []string{"music"},
			want:     false,
		},
		{
			name:     "empty interests never match",
			category: "music",
			tags:     []string{"music"},
			slugs:    nil,
			want:     false,
		},
		{
			name:     "blank slug ignored",
			category: "art",
			tags:     nil,
			slugs:    []string{"", "  "},
			want:     false,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := MatchesInterest(tt.category, tt.tags, tt.slugs); got != tt.want {
				t.Fatalf("MatchesInterest() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestScoreComment_InterestMatchRanksHigher(t *testing.T) {
	now := time.Date(2026, 7, 19, 12, 0, 0, 0, time.UTC)
	created := now.Add(-2 * time.Hour)

	base := ScoreInput{
		CreatedAt:    created,
		LikesCount:   4,
		RepliesCount: 1,
		Now:          now,
	}

	matched := base
	matched.PostCategory = "music"
	matched.PostTags = []string{"live"}
	matched.InterestSlugs = []string{"music", "cinema"}

	unrelated := base
	unrelated.PostCategory = "sports"
	unrelated.PostTags = []string{"football"}
	unrelated.InterestSlugs = []string{"music", "cinema"}

	matchedScore := ScoreComment(matched)
	unrelatedScore := ScoreComment(unrelated)

	if matchedScore <= unrelatedScore {
		t.Fatalf("interest match should rank higher: matched=%v unrelated=%v", matchedScore, unrelatedScore)
	}
	if matchedScore-unrelatedScore < interestBoost-0.001 {
		t.Fatalf("expected boost of ~%v, delta=%v", interestBoost, matchedScore-unrelatedScore)
	}
}

func TestScoreComment_NoInterestsUsesEngagementAndRecency(t *testing.T) {
	now := time.Date(2026, 7, 19, 12, 0, 0, 0, time.UTC)

	hot := ScoreComment(ScoreInput{
		CreatedAt:    now.Add(-1 * time.Hour),
		LikesCount:   20,
		RepliesCount: 5,
		PostCategory: "music",
		Now:          now,
	})
	cold := ScoreComment(ScoreInput{
		CreatedAt:    now.Add(-48 * time.Hour),
		LikesCount:   0,
		RepliesCount: 0,
		PostCategory: "music",
		Now:          now,
	})

	if hot <= cold {
		t.Fatalf("higher engagement+recency should win without interests: hot=%v cold=%v", hot, cold)
	}
}

func TestScoreComment_TableDrivenRanking(t *testing.T) {
	now := time.Date(2026, 7, 19, 18, 0, 0, 0, time.UTC)
	interests := []string{"technology"}

	tests := []struct {
		name string
		a    ScoreInput
		b    ScoreInput
	}{
		{
			name: "tag match beats equal engagement non-match",
			a: ScoreInput{
				CreatedAt:     now.Add(-3 * time.Hour),
				LikesCount:    3,
				RepliesCount:  0,
				PostCategory:  "general",
				PostTags:      []string{"Technology"},
				InterestSlugs: interests,
				Now:           now,
			},
			b: ScoreInput{
				CreatedAt:     now.Add(-3 * time.Hour),
				LikesCount:    3,
				RepliesCount:  0,
				PostCategory:  "food",
				PostTags:      []string{"recipes"},
				InterestSlugs: interests,
				Now:           now,
			},
		},
		{
			name: "category match beats fresher unrelated when boost applies",
			a: ScoreInput{
				CreatedAt:     now.Add(-6 * time.Hour),
				LikesCount:    1,
				RepliesCount:  0,
				PostCategory:  "Technology",
				InterestSlugs: interests,
				Now:           now,
			},
			b: ScoreInput{
				CreatedAt:     now.Add(-30 * time.Minute),
				LikesCount:    1,
				RepliesCount:  0,
				PostCategory:  "travel",
				InterestSlugs: interests,
				Now:           now,
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			sa := ScoreComment(tt.a)
			sb := ScoreComment(tt.b)
			if sa <= sb {
				t.Fatalf("expected a > b: a=%v b=%v", sa, sb)
			}
		})
	}
}
