package handler

import (
	"strconv"

	"github.com/gofiber/fiber/v2"
	_ "github.com/xilo-platform/xilo/internal/media/model"
	"github.com/xilo-platform/xilo/internal/media/service"
)

type MediaHandler struct {
	svc *service.MediaService
}

func NewMediaHandler(svc *service.MediaService) *MediaHandler {
	return &MediaHandler{svc: svc}
}

// @Summary      Upload media file
// @Tags         media
// @Accept       multipart/form-data
// @Produce      json
// @Security     BearerAuth
// @Param        file formData file true "File to upload"
// @Success      201  {object}  model.UploadResponse
// @Failure      400  {object}  map[string]string
// @Router       /media/upload [post]
func (h *MediaHandler) Upload(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	return h.handleFileUpload(c, userID, false)
}

func (h *MediaHandler) UploadAvatar(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	return h.handleFileUpload(c, userID, true)
}

func (h *MediaHandler) handleFileUpload(c *fiber.Ctx, userID string, isAvatar bool) error {
	file, err := c.FormFile("file")
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "no file provided",
		})
	}

	f, err := file.Open()
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to read file",
		})
	}
	defer f.Close()

	mimeType := file.Header.Get("Content-Type")
	if mimeType == "" {
		mimeType = "application/octet-stream"
	}

	var resp interface{}
	if isAvatar {
		resp, err = h.svc.UploadAvatar(c.UserContext(), userID, file.Filename, f, file.Size, mimeType)
	} else {
		resp, err = h.svc.Upload(c.UserContext(), userID, file.Filename, f, file.Size, mimeType)
	}

	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": err.Error(),
		})
	}

	return c.Status(fiber.StatusCreated).JSON(resp)
}

// @Summary      Get media by ID
// @Tags         media
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Media ID"
// @Success      200  {object}  model.Media
// @Failure      404  {object}  map[string]string
// @Router       /media/{id} [get]
func (h *MediaHandler) Get(c *fiber.Ctx) error {
	id := c.Params("id")
	media, err := h.svc.GetByID(c.UserContext(), id)
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{
			"error": "media not found",
		})
	}
	return c.JSON(media)
}

// @Summary      List user media
// @Tags         media
// @Produce      json
// @Security     BearerAuth
// @Param        cursor query string false "Pagination cursor"
// @Param        limit query int false "Items per page" default(20)
// @Success      200  {object}  map[string]interface{}
// @Router       /media [get]
func (h *MediaHandler) List(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	cursor := c.Query("cursor")
	limit, _ := strconv.Atoi(c.Query("limit", "20"))

	items, nextCursor, err := h.svc.ListByUser(c.UserContext(), userID, cursor, limit)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "failed to list media",
		})
	}

	return c.JSON(fiber.Map{
		"data":        items,
		"next_cursor": nextCursor,
		"has_more":    nextCursor != "",
	})
}

// @Summary      Delete media
// @Tags         media
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Media ID"
// @Success      200  {object}  map[string]string
// @Failure      400  {object}  map[string]string
// @Router       /media/{id} [delete]
func (h *MediaHandler) Delete(c *fiber.Ctx) error {
	userID := c.Locals("userID").(string)
	id := c.Params("id")

	if err := h.svc.Delete(c.UserContext(), id, userID); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": err.Error(),
		})
	}

	return c.JSON(fiber.Map{"message": "media deleted"})
}
