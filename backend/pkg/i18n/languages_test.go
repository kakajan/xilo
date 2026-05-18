package i18n

import "testing"

func TestIsValidLanguage(t *testing.T) {
	tests := []struct {
		code string
		want bool
	}{
		{"fa", true},
		{"en", true},
		{"ar", true},
		{"ru", true},
		{"tr", true},
		{"xx", false},
		{"", false},
		{"FA", false},
		{"farsi", false},
	}

	for _, tt := range tests {
		t.Run(tt.code, func(t *testing.T) {
			if got := IsValidLanguage(tt.code); got != tt.want {
				t.Errorf("IsValidLanguage(%q) = %v, want %v", tt.code, got, tt.want)
			}
		})
	}
}

func TestGetDirection(t *testing.T) {
	tests := []struct {
		code string
		want string
	}{
		{"fa", "rtl"},
		{"en", "ltr"},
		{"ar", "rtl"},
		{"ru", "ltr"},
		{"tr", "ltr"},
		{"xx", "rtl"},
		{"", "rtl"},
	}

	for _, tt := range tests {
		t.Run(tt.code, func(t *testing.T) {
			if got := GetDirection(tt.code); got != tt.want {
				t.Errorf("GetDirection(%q) = %q, want %q", tt.code, got, tt.want)
			}
		})
	}
}

func TestGetLanguage(t *testing.T) {
	lang, ok := GetLanguage("fa")
	if !ok {
		t.Fatal("GetLanguage(\"fa\") returned ok=false")
	}
	if lang.Code != "fa" {
		t.Errorf("GetLanguage(\"fa\").Code = %q, want \"fa\"", lang.Code)
	}
	if lang.NameNative != "فارسی" {
		t.Errorf("GetLanguage(\"fa\").NameNative = %q, want \"فارسی\"", lang.NameNative)
	}
	if lang.Direction != "rtl" {
		t.Errorf("GetLanguage(\"fa\").Direction = %q, want \"rtl\"", lang.Direction)
	}

	_, ok = GetLanguage("xx")
	if ok {
		t.Error("GetLanguage(\"xx\") returned ok=true, want false")
	}
}

func TestListLanguages(t *testing.T) {
	languages := ListLanguages()
	if len(languages) != len(SupportedLanguages) {
		t.Errorf("ListLanguages() returned %d languages, want %d", len(languages), len(SupportedLanguages))
	}

	codes := make(map[string]bool)
	for _, lang := range languages {
		codes[lang.Code] = true
	}

	for code := range SupportedLanguages {
		if !codes[code] {
			t.Errorf("ListLanguages() missing language %q", code)
		}
	}
}

func TestDefaultLanguage(t *testing.T) {
	if DefaultLanguage != "fa" {
		t.Errorf("DefaultLanguage = %q, want \"fa\"", DefaultLanguage)
	}
	if !IsValidLanguage(DefaultLanguage) {
		t.Errorf("DefaultLanguage %q is not a valid language", DefaultLanguage)
	}
}
