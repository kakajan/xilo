// Phase 5: AI-powered content recommendations
// Implementation: collaborative filtering via user reading history + content embeddings
// Technology: meilisearch similar-documents, Redis vector search, or olama embeddings
// Status: SPEC ONLY — implementation deferred

package ai

// RecommendationEngine suggests posts based on user interaction patterns
type RecommendationEngine interface {
	GetRecommendations(userID string, limit int) ([]string, error)
	TrainOnInteraction(userID, postID, interactionType string) error
}
