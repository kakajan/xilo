package theme

import (
	"encoding/json"
	"testing"
)

func TestDefaultValid(t *testing.T) {
	if err := Validate(Default()); err != nil {
		t.Fatalf("default theme invalid: %v", err)
	}
}

func TestValidateRejectsBadHex(t *testing.T) {
	s := Default()
	s.Light.Primary = "blue"
	if err := Validate(s); err == nil {
		t.Fatal("expected validation error")
	}
}

func TestMergePartial(t *testing.T) {
	base := Default()
	patch := Settings{Light: Palette{Primary: "#ff0000"}}
	merged := Merge(base, patch)
	if merged.Light.Primary != "#ff0000" {
		t.Fatalf("primary not patched: %s", merged.Light.Primary)
	}
	if merged.Light.Background != base.Light.Background {
		t.Fatalf("background should stay default")
	}
}

func TestParseDefaultsOnEmpty(t *testing.T) {
	got, err := Parse(nil)
	if err != nil {
		t.Fatal(err)
	}
	if got.Light.Primary != Default().Light.Primary {
		t.Fatalf("unexpected primary %s", got.Light.Primary)
	}
}

func TestParseRoundTrip(t *testing.T) {
	raw, err := json.Marshal(Default())
	if err != nil {
		t.Fatal(err)
	}
	got, err := Parse(raw)
	if err != nil {
		t.Fatal(err)
	}
	if got.Dark.BackgroundTertiary != "#22303C" {
		t.Fatalf("unexpected dark tertiary %s", got.Dark.BackgroundTertiary)
	}
}
