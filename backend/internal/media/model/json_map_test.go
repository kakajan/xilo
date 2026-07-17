package model

import (
	"testing"
)

func TestJSONMap_ValueAndScan(t *testing.T) {
	original := JSONMap{"avatar": "https://cdn.example/a.png"}
	val, err := original.Value()
	if err != nil {
		t.Fatalf("Value: %v", err)
	}
	raw, ok := val.([]byte)
	if !ok {
		t.Fatalf("expected []byte, got %T", val)
	}

	var scanned JSONMap
	if err := scanned.Scan(raw); err != nil {
		t.Fatalf("Scan: %v", err)
	}
	if scanned["avatar"] != original["avatar"] {
		t.Fatalf("got %#v want %#v", scanned, original)
	}
}

func TestJSONMap_ScanNil(t *testing.T) {
	var m JSONMap
	if err := m.Scan(nil); err != nil {
		t.Fatalf("Scan nil: %v", err)
	}
	if len(m) != 0 {
		t.Fatalf("expected empty map, got %#v", m)
	}
}
