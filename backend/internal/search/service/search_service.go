package service

import (
	"context"

	"github.com/xilo-platform/xilo/internal/search/model"
	"github.com/xilo-platform/xilo/internal/search/repository"
)

type SearchService struct {
	repo *repository.SearchRepo
}

func NewSearchService(repo *repository.SearchRepo) *SearchService {
	return &SearchService{repo: repo}
}

func (s *SearchService) IndexPost(ctx context.Context, doc *model.PostDocument) error {
	return s.repo.IndexPost(ctx, doc)
}

func (s *SearchService) UpdatePost(ctx context.Context, doc *model.PostDocument) error {
	return s.repo.UpdatePost(ctx, doc)
}

func (s *SearchService) DeletePost(ctx context.Context, id string) error {
	return s.repo.DeletePost(ctx, id)
}

func (s *SearchService) Search(ctx context.Context, params *model.SearchParams) ([]*model.SearchResult, int64, error) {
	if params.Limit == 0 {
		params.Limit = 20
	}
	if params.Limit > 100 {
		params.Limit = 100
	}
	if params.Query == "" && params.Category == "" && params.Tag == "" {
		params.Query = ""
	}
	return s.repo.Search(ctx, params)
}

func (s *SearchService) Suggest(ctx context.Context, query string) ([]*model.SuggestItem, error) {
	if len(query) < 2 {
		return nil, nil
	}
	return s.repo.Suggest(ctx, query)
}
