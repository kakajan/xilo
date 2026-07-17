package brand

import (
	"encoding/json"
	"testing"
)

func TestDefaultValid(t *testing.T) {
	if err := Validate(Default()); err != nil {
		t.Fatalf("default brand invalid: %v", err)
	}
	d := Default()
	if d.NameFA != "آیله" || d.NameEN != "aile" || d.Display != "آیله | aile" {
		t.Fatalf("unexpected default: %+v", d)
	}
}

func TestValidateRejectsEmpty(t *testing.T) {
	s := Default()
	s.NameFA = "  "
	if err := Validate(Normalize(s)); err == nil {
		t.Fatal("expected validation error for empty name_fa")
	}
}

func TestMergePartial(t *testing.T) {
	base := Default()
	patch := Settings{NameEN: "Aile"}
	merged := Merge(base, patch)
	if merged.NameEN != "Aile" {
		t.Fatalf("name_en not patched: %s", merged.NameEN)
	}
	if merged.NameFA != base.NameFA {
		t.Fatalf("name_fa should stay default")
	}
}

func TestParseDefaultsOnEmpty(t *testing.T) {
	got, err := Parse(nil)
	if err != nil {
		t.Fatal(err)
	}
	if got.Display != Default().Display {
		t.Fatalf("unexpected display %s", got.Display)
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
	if got.NameFA != "آیله" {
		t.Fatalf("unexpected name_fa %s", got.NameFA)
	}
}
