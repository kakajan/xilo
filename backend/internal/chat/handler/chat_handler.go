package handler

import (
	"errors"
	"log/slog"
	"strconv"
	"strings"
	"time"

	"github.com/gofiber/fiber/v2"
	"github.com/xilo-platform/xilo/internal/chat/model"
	"github.com/xilo-platform/xilo/internal/chat/service"
	pkgidempotency "github.com/xilo-platform/xilo/pkg/idempotency"
	"github.com/xilo-platform/xilo/pkg/pagination"
)

type ChatHandler struct {
	svc   *service.ChatService
	clock func() time.Time
}

func NewChatHandler(svc *service.ChatService) *ChatHandler {
	return &ChatHandler{
		svc:   svc,
		clock: func() time.Time { return time.Now().UTC() },
	}
}

// @Summary      List chats
// @Description  List only the authenticated user's active chat memberships
// @Tags         chats
// @Produce      json
// @Security     BearerAuth
// @Param        cursor query string false "Opaque pagination cursor"
// @Param        limit query int false "Items per page" default(20) maximum(50)
// @Success      200 {object} pagination.Result[model.Chat]
// @Failure      400 {object} model.ErrorResponse
// @Failure      401 {object} model.ErrorResponse
// @Router       /chats [get]
func (h *ChatHandler) ListChats(c *fiber.Ctx) error {
	params, err := listParams(c, 20)
	if err != nil {
		return writeError(c, &service.Error{
			Code:    service.CodeValidation,
			Message: "limit must be an integer",
		})
	}
	chats, nextCursor, err := h.svc.ListChats(c.UserContext(), userID(c), params)
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(pagination.Result[*model.Chat]{
		Data:       chats,
		NextCursor: nextCursor,
		HasMore:    nextCursor != "",
	})
}

// @Summary      Create a chat
// @Description  Create a group chat or create/reuse a direct chat
// @Tags         chats
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        Idempotency-Key header string true "UUIDv4 generated once per semantic operation"
// @Param        request body model.CreateChatRequest true "Chat data"
// @Success      201 {object} model.Chat
// @Failure      400 {object} model.ErrorResponse
// @Failure      401 {object} model.ErrorResponse
// @Failure      409 {object} model.ErrorResponse
// @Failure      503 {object} model.ErrorResponse
// @Router       /chats [post]
func (h *ChatHandler) CreateChat(c *fiber.Ctx) error {
	receivedAt := h.clock().UTC()
	idempotencyKey, err := requiredIdempotencyKey(c)
	if err != nil {
		return writeError(c, err)
	}
	var req model.CreateChatRequest
	if err := c.BodyParser(&req); err != nil {
		return writeInvalidBody(c)
	}
	result, err := h.svc.CreateChat(
		c.UserContext(),
		userID(c),
		idempotencyKey,
		receivedAt,
		&req,
	)
	if err != nil {
		return writeError(c, err)
	}
	return writeMutationResult(c, result)
}

// @Summary      Get chat
// @Description  Get chat details when the authenticated user is a member
// @Tags         chats
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Chat ID"
// @Success      200 {object} model.Chat
// @Failure      404 {object} model.ErrorResponse
// @Router       /chats/{id} [get]
func (h *ChatHandler) GetChat(c *fiber.Ctx) error {
	chat, err := h.svc.GetChat(c.UserContext(), userID(c), c.Params("id"))
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(chat)
}

// @Summary      Update chat
// @Description  Group admins update metadata; each member controls their mute/archive state
// @Tags         chats
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Chat ID"
// @Param        request body model.UpdateChatRequest true "Chat update"
// @Success      200 {object} model.Chat
// @Failure      400 {object} model.ErrorResponse
// @Failure      403 {object} model.ErrorResponse
// @Router       /chats/{id} [patch]
func (h *ChatHandler) UpdateChat(c *fiber.Ctx) error {
	var req model.UpdateChatRequest
	if err := c.BodyParser(&req); err != nil {
		return writeInvalidBody(c)
	}
	chat, err := h.svc.UpdateChat(c.UserContext(), userID(c), c.Params("id"), &req)
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(chat)
}

// @Summary      Leave or archive chat
// @Description  Leave a group chat, or archive a direct chat for the current user
// @Tags         chats
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Chat ID"
// @Success      200 {object} map[string]string
// @Failure      404 {object} model.ErrorResponse
// @Router       /chats/{id} [delete]
func (h *ChatHandler) LeaveChat(c *fiber.Ctx) error {
	if err := h.svc.LeaveChat(c.UserContext(), userID(c), c.Params("id")); err != nil {
		return writeError(c, err)
	}
	return c.JSON(fiber.Map{"message": "chat left", "code": "chat_left"})
}

// @Summary      Add group members
// @Description  Add members to a group chat as a group admin
// @Tags         chats
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Chat ID"
// @Param        request body model.AddMembersRequest true "Member IDs"
// @Success      200 {object} model.Chat
// @Failure      403 {object} model.ErrorResponse
// @Router       /chats/{id}/members [post]
func (h *ChatHandler) AddMembers(c *fiber.Ctx) error {
	var req model.AddMembersRequest
	if err := c.BodyParser(&req); err != nil {
		return writeInvalidBody(c)
	}
	chat, err := h.svc.AddMembers(c.UserContext(), userID(c), c.Params("id"), &req)
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(chat)
}

// @Summary      Remove or leave group
// @Description  Group admins remove another member; any group member may remove themselves
// @Tags         chats
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Chat ID"
// @Param        userId path string true "User ID"
// @Success      200 {object} map[string]string
// @Failure      403 {object} model.ErrorResponse
// @Router       /chats/{id}/members/{userId} [delete]
func (h *ChatHandler) RemoveMember(c *fiber.Ctx) error {
	err := h.svc.RemoveMember(
		c.UserContext(),
		userID(c),
		c.Params("id"),
		c.Params("userId"),
	)
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(fiber.Map{"message": "member removed", "code": "member_removed"})
}

// @Summary      List messages
// @Description  List chat messages using opaque cursor pagination
// @Tags         chats
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Chat ID"
// @Param        cursor query string false "Opaque pagination cursor"
// @Param        limit query int false "Items per page" default(50) maximum(100)
// @Success      200 {object} pagination.Result[model.Message]
// @Failure      404 {object} model.ErrorResponse
// @Router       /chats/{id}/messages [get]
func (h *ChatHandler) ListMessages(c *fiber.Ctx) error {
	params, err := listParams(c, 50)
	if err != nil {
		return writeError(c, &service.Error{
			Code:    service.CodeValidation,
			Message: "limit must be an integer",
		})
	}
	messages, nextCursor, err := h.svc.ListMessages(
		c.UserContext(),
		userID(c),
		c.Params("id"),
		params,
	)
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(pagination.Result[*model.Message]{
		Data:       messages,
		NextCursor: nextCursor,
		HasMore:    nextCursor != "",
	})
}

// @Summary      Send message
// @Description  Persist a message for a chat member; realtime delivery is not part of this endpoint
// @Tags         chats
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Chat ID"
// @Param        Idempotency-Key header string true "UUIDv4 generated once per semantic operation"
// @Param        request body model.CreateMessageRequest true "Message data"
// @Success      201 {object} model.Message
// @Failure      400 {object} model.ErrorResponse
// @Failure      404 {object} model.ErrorResponse
// @Failure      409 {object} model.ErrorResponse
// @Failure      503 {object} model.ErrorResponse
// @Router       /chats/{id}/messages [post]
func (h *ChatHandler) CreateMessage(c *fiber.Ctx) error {
	receivedAt := h.clock().UTC()
	idempotencyKey, err := requiredIdempotencyKey(c)
	if err != nil {
		return writeError(c, err)
	}
	var req model.CreateMessageRequest
	if err := c.BodyParser(&req); err != nil {
		return writeInvalidBody(c)
	}
	result, err := h.svc.CreateMessage(
		c.UserContext(),
		userID(c),
		c.Params("id"),
		idempotencyKey,
		receivedAt,
		&req,
	)
	if err != nil {
		return writeError(c, err)
	}
	return writeMutationResult(c, result)
}

// @Summary      Edit message
// @Description  Edit a sender-owned message within 48 hours
// @Tags         chats
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Message ID"
// @Param        request body model.UpdateMessageRequest true "Updated content"
// @Success      200 {object} model.Message
// @Failure      403 {object} model.ErrorResponse
// @Failure      409 {object} model.ErrorResponse
// @Router       /messages/{id} [patch]
func (h *ChatHandler) UpdateMessage(c *fiber.Ctx) error {
	var req model.UpdateMessageRequest
	if err := c.BodyParser(&req); err != nil {
		return writeInvalidBody(c)
	}
	message, err := h.svc.UpdateMessage(
		c.UserContext(),
		userID(c),
		c.Params("id"),
		&req,
	)
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(message)
}

// @Summary      Delete message
// @Description  Soft-delete a sender-owned message
// @Tags         chats
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Message ID"
// @Success      200 {object} map[string]string
// @Failure      403 {object} model.ErrorResponse
// @Router       /messages/{id} [delete]
func (h *ChatHandler) DeleteMessage(c *fiber.Ctx) error {
	if err := h.svc.DeleteMessage(c.UserContext(), userID(c), c.Params("id")); err != nil {
		return writeError(c, err)
	}
	return c.JSON(fiber.Map{"message": "message deleted", "code": "message_deleted"})
}

// @Summary      Mark message read
// @Description  Create or advance the current member's read receipt
// @Tags         chats
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Message ID"
// @Success      200 {object} model.Read
// @Failure      404 {object} model.ErrorResponse
// @Router       /messages/{id}/read [post]
func (h *ChatHandler) MarkRead(c *fiber.Ctx) error {
	read, err := h.svc.MarkRead(c.UserContext(), userID(c), c.Params("id"))
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(read)
}

// @Summary      Toggle message reaction
// @Description  Add or remove one supported reaction for the current member
// @Tags         chats
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Message ID"
// @Param        request body model.ReactionRequest true "Reaction"
// @Success      200 {object} model.ReactionResult
// @Failure      400 {object} model.ErrorResponse
// @Router       /messages/{id}/reactions [post]
func (h *ChatHandler) ToggleReaction(c *fiber.Ctx) error {
	var req model.ReactionRequest
	if err := c.BodyParser(&req); err != nil {
		return writeInvalidBody(c)
	}
	result, err := h.svc.ToggleReaction(
		c.UserContext(),
		userID(c),
		c.Params("id"),
		&req,
	)
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(result)
}

// @Summary      Search chat messages
// @Description  Search message text and sender names within a member's chat
// @Tags         chats
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Chat ID"
// @Param        q query string true "Search query"
// @Param        cursor query string false "Opaque pagination cursor"
// @Param        limit query int false "Items per page" default(50) maximum(100)
// @Success      200 {object} pagination.Result[model.Message]
// @Failure      400 {object} model.ErrorResponse
// @Failure      404 {object} model.ErrorResponse
// @Router       /chats/{id}/search [get]
func (h *ChatHandler) SearchMessages(c *fiber.Ctx) error {
	params, err := listParams(c, 50)
	if err != nil {
		return writeError(c, &service.Error{
			Code:    service.CodeValidation,
			Message: "limit must be an integer",
		})
	}
	messages, nextCursor, err := h.svc.SearchMessages(
		c.UserContext(),
		userID(c),
		c.Params("id"),
		c.Query("q"),
		params,
	)
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(pagination.Result[*model.Message]{
		Data:       messages,
		NextCursor: nextCursor,
		HasMore:    nextCursor != "",
	})
}

func listParams(c *fiber.Ctx, defaultLimit int) (model.ListParams, error) {
	rawLimit := c.Query("limit")
	limit := defaultLimit
	if rawLimit != "" {
		parsed, err := strconv.Atoi(rawLimit)
		if err != nil {
			return model.ListParams{}, err
		}
		limit = parsed
	}
	return model.ListParams{Cursor: c.Query("cursor"), Limit: limit}, nil
}

func userID(c *fiber.Ctx) string {
	value, _ := c.Locals("userID").(string)
	return value
}

func requiredIdempotencyKey(c *fiber.Ctx) (string, error) {
	value := strings.TrimSpace(c.Get("Idempotency-Key"))
	if value == "" {
		return "", &service.Error{
			Code:    service.CodeIdempotencyNeeded,
			Message: "Idempotency-Key header is required",
		}
	}
	key, err := pkgidempotency.ParseKey(value)
	if err != nil {
		return "", &service.Error{
			Code:    service.CodeIdempotencyInvalid,
			Message: "Idempotency-Key must be a UUIDv4",
			Cause:   err,
		}
	}
	return key.String(), nil
}

func writeMutationResult[T any](
	c *fiber.Ctx,
	result *pkgidempotency.MutationResult[T],
) error {
	if result == nil || result.ResponseStatus < 200 || result.ResponseStatus > 299 {
		return writeError(c, &service.Error{
			Code:    service.CodeInternal,
			Message: "internal server error",
		})
	}
	if result.IsReplay() {
		if len(result.ReplayJSON) == 0 {
			return writeError(c, &service.Error{
				Code:    service.CodeInternal,
				Message: "internal server error",
			})
		}
		c.Status(result.ResponseStatus)
		c.Type("json")
		return c.Send(result.ReplayJSON)
	}
	if result.Value == nil {
		return writeError(c, &service.Error{
			Code:    service.CodeInternal,
			Message: "internal server error",
		})
	}
	return c.Status(result.ResponseStatus).JSON(result.Value)
}

func writeInvalidBody(c *fiber.Ctx) error {
	return writeError(c, &service.Error{
		Code:    service.CodeValidation,
		Message: "invalid request body",
	})
}

func writeError(c *fiber.Ctx, err error) error {
	var serviceErr *service.Error
	if !errors.As(err, &serviceErr) {
		slog.Error("unclassified chat handler error", "error", err)
		serviceErr = &service.Error{
			Code:    service.CodeInternal,
			Message: "internal server error",
		}
	}

	status := fiber.StatusInternalServerError
	switch serviceErr.Code {
	case service.CodeValidation,
		service.CodeInvalidCursor,
		service.CodeIdempotencyNeeded,
		service.CodeIdempotencyInvalid:
		status = fiber.StatusBadRequest
	case service.CodeForbidden:
		status = fiber.StatusForbidden
	case service.CodeNotFound:
		status = fiber.StatusNotFound
	case service.CodeConflict, service.CodeEditWindowExpired, service.CodeIdempotencyConflict:
		status = fiber.StatusConflict
	case service.CodeRetryable:
		status = fiber.StatusServiceUnavailable
		c.Set("Retry-After", "1")
	}
	if status >= fiber.StatusInternalServerError {
		slog.Error("chat request failed", "code", serviceErr.Code, "error", serviceErr.Cause)
	}
	return c.Status(status).JSON(model.ErrorResponse{
		Error: serviceErr.Message,
		Code:  serviceErr.Code,
	})
}
