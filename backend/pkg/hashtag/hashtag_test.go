package hashtag

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"runtime"
	"testing"
)

func TestExtract_SharedFixtures(t *testing.T) {
	_, thisFile, _, ok := runtime.Caller(0)
	if !ok {
		t.Fatal("runtime.Caller failed")
	}
	root := filepath.Clean(filepath.Join(filepath.Dir(thisFile), "..", "..", ".."))
	path := filepath.Join(root, "testdata", "hashtags", "fixtures.json")
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read fixtures: %v", err)
	}
	var doc struct {
		Cases []struct {
			ID       string   `json:"id"`
			Input    string   `json:"input"`
			Expected []string `json:"expected"`
		} `json:"cases"`
	}
	if err := json.Unmarshal(data, &doc); err != nil {
		t.Fatalf("parse fixtures: %v", err)
	}
	for _, c := range doc.Cases {
		got := Extract(c.Input)
		if !reflect.DeepEqual(got, c.Expected) {
			t.Fatalf("%s: got %v want %v", c.ID, got, c.Expected)
		}
	}
}

func TestExtract_PersianAndLatin(t *testing.T) {
	got := Extract("سلام #خبر و #Xilo_App امروز")
	want := []string{"خبر", "xilo_app"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
}

func TestExtract_SkipsURLFragment(t *testing.T) {
	got := Extract("see https://example.com/path#fragment and #real")
	want := []string{"real"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
}

func TestExtract_RejectsDigitsOnly(t *testing.T) {
	got := Extract("code #123 and #ok1")
	want := []string{"ok1"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
}

func TestExtract_DedupeCaseInsensitive(t *testing.T) {
	got := Extract("#News #news #NEWS")
	want := []string{"news"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
}

func TestExtract_BoundaryAndPunctuation(t *testing.T) {
	got := Extract("(#خبر) #دوم!")
	want := []string{"خبر", "دوم"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
}

func TestMerge_Cap10(t *testing.T) {
	var extracted []string
	for i := 1; i <= 8; i++ {
		extracted = append(extracted, Normalize(string(rune('a'+i-1))+"tag"))
	}
	explicit := []string{"x1", "x2", "x3", "x4"}
	got := Merge(extracted, explicit)
	if len(got) != 10 {
		t.Fatalf("expected 10 tags, got %d: %v", len(got), got)
	}
	if got[8] != "x1" || got[9] != "x2" {
		t.Fatalf("expected explicit fill after extract, got %v", got)
	}
}

func TestMerge_ExtractFirstThenExplicit(t *testing.T) {
	got := Merge(Extract("hello #alpha"), []string{"Beta", "alpha"})
	want := []string{"alpha", "beta"}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("got %v want %v", got, want)
	}
}

func TestPlainTextFromTipTap(t *testing.T) {
	raw := `{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"Hi #tag"}]}]}`
	got := PlainTextFromContent("", raw)
	if !reflect.DeepEqual(Extract(got), []string{"tag"}) {
		t.Fatalf("unexpected extract from tiptap: %q -> %v", got, Extract(got))
	}
}

func TestNormalize_Invalid(t *testing.T) {
	if Normalize("#") != "" || Normalize("###") != "" || Normalize("a b") != "" {
		t.Fatal("expected empty for invalid tags")
	}
}
