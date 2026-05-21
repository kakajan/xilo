package model

// PostRef is a minimal post reference embedded in profile reply items.
type PostRef struct {
	ID    string `json:"id"`
	Title string `json:"title"`
	Slug  string `json:"slug"`
}
