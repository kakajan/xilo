package jsonutil

// OrEmpty returns an empty non-nil slice when items is nil so encoding/json
// emits `[]` instead of `null` for list payloads.
func OrEmpty[T any](items []T) []T {
	if items == nil {
		return []T{}
	}
	return items
}
