package i18n

type Language struct {
	Code        string `json:"code"`
	NameNative  string `json:"name_native"`
	NameEnglish string `json:"name_english"`
	Direction   string `json:"direction"`
}

const DefaultLanguage = "fa"

var SupportedLanguages = map[string]Language{
	"fa": {Code: "fa", NameNative: "فارسی", NameEnglish: "Persian", Direction: "rtl"},
	"en": {Code: "en", NameNative: "English", NameEnglish: "English", Direction: "ltr"},
	"ar": {Code: "ar", NameNative: "العربية", NameEnglish: "Arabic", Direction: "rtl"},
	"ru": {Code: "ru", NameNative: "Русский", NameEnglish: "Russian", Direction: "ltr"},
	"tr": {Code: "tr", NameNative: "Türkçe", NameEnglish: "Turkish", Direction: "ltr"},
}

func IsValidLanguage(code string) bool {
	_, ok := SupportedLanguages[code]
	return ok
}

func GetDirection(code string) string {
	if lang, ok := SupportedLanguages[code]; ok {
		return lang.Direction
	}
	return SupportedLanguages[DefaultLanguage].Direction
}

func GetLanguage(code string) (Language, bool) {
	lang, ok := SupportedLanguages[code]
	return lang, ok
}

func ListLanguages() []Language {
	languages := make([]Language, 0, len(SupportedLanguages))
	for _, lang := range SupportedLanguages {
		languages = append(languages, lang)
	}
	return languages
}
