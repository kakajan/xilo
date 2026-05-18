package repository

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/meilisearch/meilisearch-go"
	"github.com/xilo-platform/xilo/internal/search/model"
)

const indexName = "posts"

type SearchRepo struct {
	client meilisearch.ServiceManager
}

func NewSearchRepo(host, apiKey string) (*SearchRepo, error) {
	client := meilisearch.New(host, meilisearch.WithAPIKey(apiKey))

	_, err := client.GetIndex(indexName)
	if err != nil {
		_, err := client.CreateIndex(&meilisearch.IndexConfig{
			Uid:        indexName,
			PrimaryKey: "id",
		})
		if err != nil && !strings.Contains(err.Error(), "already exists") {
			return nil, fmt.Errorf("create index: %w", err)
		}

		idx := client.Index(indexName)
		_, err = idx.UpdateSettings(&meilisearch.Settings{
			SearchableAttributes: []string{"title", "excerpt", "content_md", "tags", "author_name"},
			FilterableAttributes: []string{"category", "tags", "author_id", "published_at", "reading_time", "_language"},
			SortableAttributes:   []string{"published_at"},
			RankingRules:         []string{"words", "typo", "proximity", "attribute", "sort", "exactness"},
		})
		if err != nil {
			return nil, fmt.Errorf("update settings: %w", err)
		}
	}

	return &SearchRepo{client: client}, nil
}

func (r *SearchRepo) IndexPost(ctx context.Context, doc *model.PostDocument) error {
	_, err := r.client.Index(indexName).AddDocuments(doc, "id")
	return err
}

func (r *SearchRepo) UpdatePost(ctx context.Context, doc *model.PostDocument) error {
	docs := []*model.PostDocument{doc}
	_, err := r.client.Index(indexName).UpdateDocuments(docs, "id")
	return err
}

func (r *SearchRepo) DeletePost(ctx context.Context, id string) error {
	_, err := r.client.Index(indexName).DeleteDocument(id)
	return err
}

func (r *SearchRepo) Search(ctx context.Context, params *model.SearchParams) ([]*model.SearchResult, int64, error) {
	if params.Limit <= 0 || params.Limit > 100 {
		params.Limit = 20
	}

	req := &meilisearch.SearchRequest{
		Limit:  int64(params.Limit),
		Offset: int64(params.Offset),
		AttributesToHighlight: []string{"title", "excerpt", "content_md"},
	}

	if params.Query != "" {
		req.Query = params.Query
	}

	filters := []string{}

	if params.Category != "" {
		escaped := escapeFilterValue(params.Category)
		filters = append(filters, fmt.Sprintf(`category = "%s"`, escaped))
	}
	if params.Tag != "" {
		escaped := escapeFilterValue(params.Tag)
		filters = append(filters, fmt.Sprintf(`tags = "%s"`, escaped))
	}
	if params.Author != "" {
		escaped := escapeFilterValue(params.Author)
		filters = append(filters, fmt.Sprintf(`author_id = "%s"`, escaped))
	}
	if params.Language != "" {
		escaped := escapeFilterValue(params.Language)
		filters = append(filters, fmt.Sprintf(`_language = "%s"`, escaped))
	}
	if params.After != "" {
		filters = append(filters, fmt.Sprintf(`published_at > "%s"`, strings.ReplaceAll(params.After, `"`, `\"`)))
	}
	if params.Before != "" {
		filters = append(filters, fmt.Sprintf(`published_at < "%s"`, strings.ReplaceAll(params.Before, `"`, `\"`)))
	}

	if len(filters) > 0 {
		req.Filter = strings.Join(filters, " AND ")
	}

	req.Sort = []string{"published_at:desc"}

	ctx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()

	result, err := r.client.Index(indexName).Search(req.Query, req)
	if err != nil {
		return nil, 0, fmt.Errorf("search: %w", err)
	}

	results := make([]*model.SearchResult, len(result.Hits))
	for i, raw := range result.Hits {
		h, ok := raw.(map[string]interface{})
		if !ok {
			continue
		}
		r := &model.SearchResult{}
		if v, ok := h["id"].(string); ok {
			r.ID = v
		}
		if v, ok := h["title"].(string); ok {
			r.Title = v
		}
		if v, ok := h["slug"].(string); ok {
			r.Slug = v
		}
		if v, ok := h["excerpt"].(string); ok {
			r.Excerpt = v
		}
		if v, ok := h["cover_image_url"].(string); ok {
			r.CoverImageURL = v
		}
		if v, ok := h["category"].(string); ok {
			r.Category = v
		}
		if v, ok := h["tags"].([]interface{}); ok {
			r.Tags = make([]string, len(v))
			for j, t := range v {
				r.Tags[j] = fmt.Sprint(t)
			}
		}
		if v, ok := h["_language"].(string); ok {
			r.Language = v
		}
		if v, ok := h["author_name"].(string); ok {
			r.AuthorName = v
		}
		if v, ok := h["author_username"].(string); ok {
			r.AuthorUsername = v
		}
		if v, ok := h["published_at"].(string); ok {
			if t, err := time.Parse(time.RFC3339, v); err == nil {
				r.PublishedAt = t
			}
		}
		if v, ok := h["word_count"].(float64); ok {
			r.WordCount = int(v)
		}
		if v, ok := h["reading_time"].(float64); ok {
			r.ReadingTime = int(v)
		}
		if v, ok := h["_formatted"].(map[string]interface{}); ok {
			r.Formatted = &model.Snippet{}
			if t, ok := v["title"].(string); ok {
				r.Formatted.Title = t
			}
			if e, ok := v["excerpt"].(string); ok {
				r.Formatted.Excerpt = e
			}
			if c, ok := v["content_md"].(string); ok {
				r.Formatted.Content = c
			}
		}
		results[i] = r
	}

	return results, result.EstimatedTotalHits, nil
}

func (r *SearchRepo) Suggest(ctx context.Context, query string) ([]*model.SuggestItem, error) {
	req := &meilisearch.SearchRequest{
		Limit:  5,
		Offset: 0,
	}

	ctx, cancel := context.WithTimeout(ctx, 3*time.Second)
	defer cancel()

	result, err := r.client.Index(indexName).Search(query, req)
	if err != nil {
		return nil, fmt.Errorf("suggest: %w", err)
	}

	var items []*model.SuggestItem
	for _, hit := range result.Hits {
		h, ok := hit.(map[string]interface{})
		if !ok {
			continue
		}
		if title, ok := h["title"].(string); ok && title != "" {
			item := &model.SuggestItem{Text: title, Type: "post"}
			if slug, ok := h["slug"].(string); ok {
				item.Slug = slug
			}
			items = append(items, item)
		}
		if len(items) >= 5 {
			break
		}
	}

	return items, nil
}

func escapeFilterValue(v string) string {
	return strings.ReplaceAll(v, `"`, `\"`)
}
