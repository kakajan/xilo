package handler

import (
	"github.com/gofiber/fiber/v2"
	"github.com/xilo-platform/xilo/internal/chat/model"
	"github.com/xilo-platform/xilo/internal/chat/service"
)

type FolderHandler struct {
	svc *service.FolderService
}

func NewFolderHandler(svc *service.FolderService) *FolderHandler {
	return &FolderHandler{svc: svc}
}

// @Summary      List chat folders
// @Tags         chat-folders
// @Produce      json
// @Security     BearerAuth
// @Success      200 {array} model.ChatFolder
// @Router       /chat-folders [get]
func (h *FolderHandler) ListFolders(c *fiber.Ctx) error {
	folders, err := h.svc.ListFolders(c.UserContext(), userID(c))
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(folders)
}

// @Summary      Create chat folder
// @Tags         chat-folders
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        request body model.CreateChatFolderRequest true "Folder data"
// @Success      201 {object} model.ChatFolder
// @Router       /chat-folders [post]
func (h *FolderHandler) CreateFolder(c *fiber.Ctx) error {
	var req model.CreateChatFolderRequest
	if err := c.BodyParser(&req); err != nil {
		return writeInvalidBody(c)
	}
	folder, err := h.svc.CreateFolder(c.UserContext(), userID(c), &req)
	if err != nil {
		return writeError(c, err)
	}
	return c.Status(fiber.StatusCreated).JSON(folder)
}

// @Summary      Update chat folder
// @Tags         chat-folders
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Folder ID"
// @Param        request body model.UpdateChatFolderRequest true "Folder update"
// @Success      200 {object} model.ChatFolder
// @Router       /chat-folders/{id} [patch]
func (h *FolderHandler) UpdateFolder(c *fiber.Ctx) error {
	var req model.UpdateChatFolderRequest
	if err := c.BodyParser(&req); err != nil {
		return writeInvalidBody(c)
	}
	folder, err := h.svc.UpdateFolder(c.UserContext(), userID(c), c.Params("id"), &req)
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(folder)
}

// @Summary      Delete chat folder
// @Tags         chat-folders
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Folder ID"
// @Success      200 {object} map[string]string
// @Router       /chat-folders/{id} [delete]
func (h *FolderHandler) DeleteFolder(c *fiber.Ctx) error {
	if err := h.svc.DeleteFolder(c.UserContext(), userID(c), c.Params("id")); err != nil {
		return writeError(c, err)
	}
	return c.JSON(fiber.Map{"message": "folder deleted"})
}

// @Summary      Set chats in folder
// @Tags         chat-folders
// @Accept       json
// @Produce      json
// @Security     BearerAuth
// @Param        id path string true "Folder ID"
// @Param        request body model.SetFolderChatsRequest true "Chat IDs"
// @Success      200 {object} model.ChatFolder
// @Router       /chat-folders/{id}/chats [put]
func (h *FolderHandler) SetFolderChats(c *fiber.Ctx) error {
	var req model.SetFolderChatsRequest
	if err := c.BodyParser(&req); err != nil {
		return writeInvalidBody(c)
	}
	folder, err := h.svc.SetFolderChats(c.UserContext(), userID(c), c.Params("id"), &req)
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(folder)
}

// @Summary      Get saved messages chat
// @Description  Get or create the authenticated user's Saved Messages chat
// @Tags         chats
// @Produce      json
// @Security     BearerAuth
// @Success      200 {object} model.Chat
// @Router       /chats/saved [get]
func (h *ChatHandler) GetSavedMessages(c *fiber.Ctx) error {
	chat, err := h.svc.EnsureSavedMessagesChat(c.UserContext(), userID(c))
	if err != nil {
		return writeError(c, err)
	}
	return c.JSON(chat)
}
