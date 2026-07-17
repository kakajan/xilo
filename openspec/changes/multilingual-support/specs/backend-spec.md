# Delta for Backend (i18n)

## ADDED Requirements

### Requirement: Post Language
The system SHALL store a `language` field on every post indicating the language in which the post was written.

#### Scenario: Create post with language
- GIVEN an authenticated author creating a post
- WHEN they submit the post with a valid language code (e.g., `fa`, `en`)
- THEN the post is saved with the specified language
- AND the language field is required (defaults to platform default `fa` if not provided)

#### Scenario: List posts filtered by language
- GIVEN any visitor requesting the post list endpoint
- WHEN they include `?language=fa` in the query
- THEN only posts with `language = 'fa'` are returned
- AND if no language filter is provided, posts in all languages are returned

#### Scenario: Invalid language code
- GIVEN an author creating or updating a post
- WHEN they provide an unsupported language code
- THEN the request is rejected with a 400 validation error

---

### Requirement: User Preferred Language
The system SHALL store a `preferred_language` field on every user to persist their UI language preference.

#### Scenario: Set user language preference
- GIVEN an authenticated user
- WHEN they update their profile with a `preferred_language` value
- THEN the preference is saved and returned in subsequent profile responses

#### Scenario: Default language for new users
- GIVEN a new user registering
- WHEN no language preference is specified
- THEN their `preferred_language` defaults to the platform default (`fa`)

---

### Requirement: Language Validation
The system SHALL validate language codes against a configurable allowlist.

#### Scenario: Valid language code
- GIVEN a request with `language=fa`
- WHEN the language code is in the allowlist
- THEN the request proceeds normally

#### Scenario: Invalid language code
- GIVEN a request with `language=xx`
- WHEN the language code is NOT in the allowlist
- THEN the request returns 400 with error message

---

### Requirement: Available Languages Endpoint
The system SHALL expose an endpoint listing all supported languages with their metadata.

#### Scenario: Get supported languages
- GIVEN any client
- WHEN they call `GET /api/languages`
- THEN the response includes all supported languages with code, name (native), name (English), and direction (rtl/ltr)

---

## MODIFIED Requirements

### Requirement: Post List Endpoint (from post-spec)
The post list endpoint SHALL accept an optional `language` query parameter to filter results by post language.

#### Scenario: Filter posts by language
- GIVEN a request to `GET /api/posts?language=en`
- WHEN posts exist in multiple languages
- THEN only English posts are returned
- AND pagination works correctly within the filtered set

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/languages` | None | List supported languages |
| GET | `/api/auth/me` | Reader+ | Get current user profile (includes `preferred_language`) |
| PATCH | `/api/auth/me` | Reader+ | Update current user profile (includes `preferred_language`) |

### GET /api/languages Response

```json
{
  "languages": [
    { "code": "fa", "name_native": "فارسی", "name_english": "Persian", "direction": "rtl" },
    { "code": "en", "name_native": "English", "name_english": "English", "direction": "ltr" },
    { "code": "ar", "name_native": "العربية", "name_english": "Arabic", "direction": "rtl" },
    { "code": "ru", "name_native": "Русский", "name_english": "Russian", "direction": "ltr" },
    { "code": "tr", "name_native": "Türkçe", "name_english": "Turkish", "direction": "ltr" }
  ],
  "default": "fa"
}
```
