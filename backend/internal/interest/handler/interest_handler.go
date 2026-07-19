package handler

import (
	"errors"
	"strings"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	"github.com/xilo-platform/xilo/internal/interest/model"
	"github.com/xilo-platform/xilo/internal/interest/repository"
)

type InterestHandler struct {
	repo *repository.InterestRepo
}

func NewInterestHandler(repo *repository.InterestRepo) *InterestHandler {
	return &InterestHandler{repo: repo}
}

// ListActive returns active interests ordered by sort_order (public).
func (h *InterestHandler) ListActive(c *fiber.Ctx) error {
	items, err := h.repo.ListActive(c.UserContext())
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to list interests",
			"code":  "internal_error",
		})
	}
	return c.JSON(fiber.Map{"interests": model.ToPublicList(items)})
}

// GetMyInterests returns the authenticated user's interest set.
func (h *InterestHandler) GetMyInterests(c *fiber.Ctx) error {
	userID, ok := c.Locals("userID").(string)
	if !ok || userID == "" {
		return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
			"error": "unauthorized",
			"code":  "unauthorized",
		})
	}

	ids, err := h.repo.GetUserInterestIDs(c.UserContext(), userID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to load user interests",
			"code":  "internal_error",
		})
	}
	items, err := h.repo.GetUserInterests(c.UserContext(), userID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to load user interests",
			"code":  "internal_error",
		})
	}
	return c.JSON(fiber.Map{
		"interest_ids": ids,
		"interests":    model.ToPublicList(items),
	})
}

// PutMyInterests replaces the authenticated user's interest set (max 20).
func (h *InterestHandler) PutMyInterests(c *fiber.Ctx) error {
	userID, ok := c.Locals("userID").(string)
	if !ok || userID == "" {
		return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
			"error": "unauthorized",
			"code":  "unauthorized",
		})
	}

	var req model.PutUserInterestsRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid request body",
			"code":  "bad_request",
		})
	}

	ids, err := model.NormalizeInterestIDs(req.InterestIDs)
	if err != nil {
		if errors.Is(err, model.ErrTooMany) {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
				"error": "maximum 20 interests allowed",
				"code":  "bad_request",
			})
		}
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid interest_ids",
			"code":  "bad_request",
		})
	}

	if len(ids) > 0 {
		count, err := h.repo.CountActiveByIDs(c.UserContext(), ids)
		if err != nil {
			return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
				"error": "failed to validate interests",
				"code":  "internal_error",
			})
		}
		if count != len(ids) {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
				"error": "one or more interest ids are invalid or inactive",
				"code":  "bad_request",
			})
		}
	}

	if err := h.repo.ReplaceUserInterests(c.UserContext(), userID, ids); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to save user interests",
			"code":  "internal_error",
		})
	}

	items, err := h.repo.GetUserInterests(c.UserContext(), userID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to load user interests",
			"code":  "internal_error",
		})
	}
	savedIDs, err := h.repo.GetUserInterestIDs(c.UserContext(), userID)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to load user interests",
			"code":  "internal_error",
		})
	}
	return c.JSON(fiber.Map{
		"interest_ids": savedIDs,
		"interests":    model.ToPublicList(items),
	})
}

// ListAll returns all interests including inactive (admin).
func (h *InterestHandler) ListAll(c *fiber.Ctx) error {
	items, err := h.repo.ListAll(c.UserContext())
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to list interests",
			"code":  "internal_error",
		})
	}
	return c.JSON(fiber.Map{"interests": items})
}

// Create adds a catalog interest (admin).
func (h *InterestHandler) Create(c *fiber.Ctx) error {
	var req model.CreateInterestRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid request body",
			"code":  "bad_request",
		})
	}

	slug := strings.TrimSpace(strings.ToLower(req.Slug))
	if err := model.ValidateInterestSlug(slug); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid slug",
			"code":  "bad_request",
		})
	}
	if err := model.ValidateLabels(req.Labels); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "labels must include non-empty en and fa",
			"code":  "bad_request",
		})
	}

	sortOrder := 0
	if req.SortOrder != nil {
		sortOrder = *req.SortOrder
	}
	isActive := true
	if req.IsActive != nil {
		isActive = *req.IsActive
	}
	var icon *string
	if req.Icon != nil {
		trimmed := strings.TrimSpace(*req.Icon)
		if trimmed != "" {
			if len(trimmed) > 64 {
				return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
					"error": "icon must be at most 64 characters",
					"code":  "bad_request",
				})
			}
			icon = &trimmed
		}
	}

	item, err := h.repo.Create(c.UserContext(), slug, req.Labels, icon, sortOrder, isActive)
	if err != nil {
		if errors.Is(err, model.ErrConflict) {
			return c.Status(fiber.StatusConflict).JSON(fiber.Map{
				"error": "slug already exists",
				"code":  "conflict",
			})
		}
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to create interest",
			"code":  "internal_error",
		})
	}
	return c.Status(fiber.StatusCreated).JSON(item)
}

// Patch partially updates a catalog interest (admin).
func (h *InterestHandler) Patch(c *fiber.Ctx) error {
	id := c.Params("id")
	if _, err := uuid.Parse(id); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid interest id",
			"code":  "bad_request",
		})
	}

	var req model.PatchInterestRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid request body",
			"code":  "bad_request",
		})
	}
	if req.Slug == nil && req.Labels == nil && req.Icon == nil && req.SortOrder == nil && req.IsActive == nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "no fields to update",
			"code":  "bad_request",
		})
	}
	if req.Slug != nil {
		slug := strings.TrimSpace(strings.ToLower(*req.Slug))
		if err := model.ValidateInterestSlug(slug); err != nil {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
				"error": "invalid slug",
				"code":  "bad_request",
			})
		}
		req.Slug = &slug
	}
	if req.Labels != nil {
		if err := model.ValidateLabels(req.Labels); err != nil {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
				"error": "labels must include non-empty en and fa",
				"code":  "bad_request",
			})
		}
	}
	if req.Icon != nil {
		trimmed := strings.TrimSpace(*req.Icon)
		if len(trimmed) > 64 {
			return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
				"error": "icon must be at most 64 characters",
				"code":  "bad_request",
			})
		}
		req.Icon = &trimmed
	}

	item, err := h.repo.Patch(c.UserContext(), id, req)
	if err != nil {
		if errors.Is(err, model.ErrNotFound) {
			return c.Status(fiber.StatusNotFound).JSON(fiber.Map{
				"error": "interest not found",
				"code":  "not_found",
			})
		}
		if errors.Is(err, model.ErrConflict) {
			return c.Status(fiber.StatusConflict).JSON(fiber.Map{
				"error": "slug already exists",
				"code":  "conflict",
			})
		}
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to update interest",
			"code":  "internal_error",
		})
	}
	return c.JSON(item)
}

// Delete soft-deactivates an interest (admin).
func (h *InterestHandler) Delete(c *fiber.Ctx) error {
	id := c.Params("id")
	if _, err := uuid.Parse(id); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid interest id",
			"code":  "bad_request",
		})
	}

	item, err := h.repo.SoftDeactivate(c.UserContext(), id)
	if err != nil {
		if errors.Is(err, model.ErrNotFound) {
			return c.Status(fiber.StatusNotFound).JSON(fiber.Map{
				"error": "interest not found",
				"code":  "not_found",
			})
		}
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to delete interest",
			"code":  "internal_error",
		})
	}
	return c.JSON(item)
}

// Reorder sets sort_order from ordered_ids (admin).
func (h *InterestHandler) Reorder(c *fiber.Ctx) error {
	var req model.ReorderRequest
	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid request body",
			"code":  "bad_request",
		})
	}

	orders, err := model.AssignSortOrders(req.OrderedIDs)
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "invalid ordered_ids",
			"code":  "bad_request",
		})
	}

	if err := h.repo.Reorder(c.UserContext(), orders); err != nil {
		if errors.Is(err, model.ErrNotFound) {
			return c.Status(fiber.StatusNotFound).JSON(fiber.Map{
				"error": "one or more interests not found",
				"code":  "not_found",
			})
		}
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to reorder interests",
			"code":  "internal_error",
		})
	}

	items, err := h.repo.ListAll(c.UserContext())
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to list interests",
			"code":  "internal_error",
		})
	}
	return c.JSON(fiber.Map{"interests": items})
}
