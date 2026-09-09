package jsonutil

import (
	"encoding/json"
	"testing"
)

func TestOrEmpty_NilSliceMarshalsAsArray(t *testing.T) {
	var comments []*struct{}
	raw, err := json.Marshal(map[string]any{"data": OrEmpty(comments)})
	if err != nil {
		t.Fatal(err)
	}
	if string(raw) != `{"data":[]}` {
		t.Fatalf("got %s", raw)
	}
}

func TestOrEmpty_KeepsNonEmpty(t *testing.T) {
	in := []string{"a"}
	got := OrEmpty(in)
	if len(got) != 1 || got[0] != "a" {
		t.Fatalf("got %#v", got)
	}
}
