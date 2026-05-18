# Delta for Search (i18n)

## ADDED Requirements

### Requirement: Language-Aware Indexing
Meilisearch SHALL index posts with their language as a filterable and facetable attribute.

#### Scenario: Index post with language
- GIVEN a post is published with `language: "en"`
- WHEN the Search Service indexes the post
- THEN the Meilisearch document includes `_language: "en"`
- AND the language field is configured as `filterableAttributes`

---

### Requirement: Language Filter in Search
The search API SHALL accept a `language` parameter to filter results by post language.

#### Scenario: Search with language filter
- GIVEN a user searches for "technology" with `?language=fa`
- WHEN the search service queries Meilisearch
- THEN only Persian posts matching "technology" are returned

#### Scenario: Search without language filter
- GIVEN a user searches without a language parameter
- WHEN the search service queries Meilisearch
- THEN posts in all languages are returned
- AND results may include a language facet count

---

### Requirement: Language-Specific Analyzers
Meilisearch SHALL use language-appropriate analyzers for better search relevance.

#### Scenario: Persian search
- GIVEN a Persian-language index
- WHEN a user searches in Persian
- THEN the Persian analyzer handles word boundaries and normalization correctly

#### Scenario: English search
- GIVEN an English-language index
- WHEN a user searches in English
- THEN the English analyzer handles stemming and stop words correctly

---

## MODIFIED Requirements

### Requirement: Search Index Configuration (from search-spec)
The Meilisearch index configuration SHALL include `language` as a filterable and facetable attribute.

```json
{
  "filterableAttributes": ["_language", "category", "tags", "status"],
  "faceting": {
    "maxValuesPerFacet": 100
  },
  "pagination": {
    "maxTotalHits": 1000
  }
}
```
