package websocket

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"strings"
	"sync"
	"time"

	fiberws "github.com/gofiber/contrib/websocket"
	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
	"github.com/xilo-platform/xilo/pkg/jwt"
	"github.com/xilo-platform/xilo/pkg/realtime"
)

const (
	maxSubscriptionsPerClient = 50
	maxConnectedClients       = 10_000
	clientSendBufferSize      = 256
	maxInboundFrameSize       = 64 << 10
	maxInboundPayloadSize     = 48 << 10

	defaultWriteWait        = 10 * time.Second
	defaultPongWait         = 60 * time.Second
	defaultPingPeriod       = 25 * time.Second
	defaultOperationWait    = 10 * time.Second
	defaultTypingInterval   = 3 * time.Second
	defaultTypingTTL        = 5 * time.Second
	defaultPresenceDebounce = 5 * time.Second
)

// Message remains source-compatible with the existing post/user broadcast API.
type Message struct {
	Event string          `json:"event"`
	Data  json.RawMessage `json:"data"`
}

type tokenValidator interface {
	ValidateToken(token string) (*jwt.Claims, error)
}

type websocketConn interface {
	ReadMessage() (messageType int, p []byte, err error)
	WriteMessage(messageType int, data []byte) error
	WriteControl(messageType int, data []byte, deadline time.Time) error
	SetReadLimit(limit int64)
	SetReadDeadline(t time.Time) error
	SetWriteDeadline(t time.Time) error
	SetPongHandler(h func(appData string) error)
	Close() error
}

type closeRequest struct {
	code   int
	reason string
}

type typingState struct {
	lastSent       time.Time
	lastActivity   time.Time
	lastAuthorized time.Time
	timer          *time.Timer
}

type presenceState struct {
	online bool
	timer  *time.Timer
}

type Client struct {
	Conn         websocketConn
	ID           string
	UserID       string
	Role         string
	Send         chan []byte
	Channels     map[string]bool
	typing       map[string]*typingState
	closeRequest chan closeRequest
	done         chan struct{}
	closeOnce    sync.Once
}

type Hub struct {
	clients       map[*Client]bool
	clientsByUser map[string]map[*Client]bool
	clientsByChan map[string]map[*Client]bool
	presence      map[string]*presenceState
	mu            sync.RWMutex
	presenceMu    sync.Mutex

	rdb       redis.UniversalClient
	jwtMgr    tokenValidator
	gateway   realtime.ChatGateway
	publisher realtime.Publisher

	ctx    context.Context
	cancel context.CancelFunc

	writeWait        time.Duration
	pongWait         time.Duration
	pingPeriod       time.Duration
	operationWait    time.Duration
	typingInterval   time.Duration
	typingTTL        time.Duration
	presenceDebounce time.Duration
}

func NewHub(rdb *redis.Client, jwtMgr *jwt.Manager) *Hub {
	var publisher realtime.Publisher = realtime.NopPublisher{}
	if rdb != nil {
		publisher = realtime.NewRedisPublisher(rdb)
	}
	return NewHubWithDependencies(rdb, jwtMgr, nil, publisher)
}

func NewHubWithDependencies(
	rdb redis.UniversalClient,
	jwtMgr tokenValidator,
	gateway realtime.ChatGateway,
	publisher realtime.Publisher,
) *Hub {
	ctx, cancel := context.WithCancel(context.Background())
	if publisher == nil {
		publisher = realtime.NopPublisher{}
	}
	return &Hub{
		clients:          make(map[*Client]bool),
		clientsByUser:    make(map[string]map[*Client]bool),
		clientsByChan:    make(map[string]map[*Client]bool),
		presence:         make(map[string]*presenceState),
		rdb:              rdb,
		jwtMgr:           jwtMgr,
		gateway:          gateway,
		publisher:        publisher,
		ctx:              ctx,
		cancel:           cancel,
		writeWait:        defaultWriteWait,
		pongWait:         defaultPongWait,
		pingPeriod:       defaultPingPeriod,
		operationWait:    defaultOperationWait,
		typingInterval:   defaultTypingInterval,
		typingTTL:        defaultTypingTTL,
		presenceDebounce: defaultPresenceDebounce,
	}
}

func (h *Hub) HandleWebSocket(conn *fiberws.Conn) {
	token := extractWSToken(conn)
	if token == "" || h.jwtMgr == nil {
		writeClose(conn, fiberws.ClosePolicyViolation, "authentication required", h.writeWait)
		_ = conn.Close()
		return
	}

	claims, err := h.jwtMgr.ValidateToken(token)
	if err != nil {
		writeClose(conn, fiberws.ClosePolicyViolation, "authentication failed", h.writeWait)
		_ = conn.Close()
		return
	}
	userID, err := canonicalUUID(claims.UserID)
	if err != nil {
		writeClose(conn, fiberws.ClosePolicyViolation, "authentication failed", h.writeWait)
		_ = conn.Close()
		return
	}

	client := newClient(conn, userID, claims.Role)
	if !h.registerClient(client) {
		writeClose(conn, fiberws.CloseTryAgainLater, "connection capacity reached", h.writeWait)
		_ = conn.Close()
		return
	}

	go client.writePump(h)
	client.readPump(h)
}

func newClient(conn websocketConn, userID string, role string) *Client {
	return &Client{
		Conn:         conn,
		ID:           uuid.NewString(),
		UserID:       userID,
		Role:         role,
		Send:         make(chan []byte, clientSendBufferSize),
		Channels:     make(map[string]bool),
		typing:       make(map[string]*typingState),
		closeRequest: make(chan closeRequest, 1),
		done:         make(chan struct{}),
	}
}

func extractWSToken(conn *fiberws.Conn) string {
	if token := strings.TrimSpace(conn.Query("token")); token != "" {
		return token
	}
	return strings.TrimSpace(conn.Cookies("xilo_access_token"))
}

func (h *Hub) registerClient(client *Client) bool {
	h.mu.Lock()
	defer h.mu.Unlock()
	if len(h.clients) >= maxConnectedClients {
		return false
	}
	h.clients[client] = true
	addToIndex(h.clientsByUser, client.UserID, client)
	return true
}

func (h *Hub) unregisterClient(client *Client) {
	var offlineChats []string
	var stoppedTyping []string

	h.mu.Lock()
	if !h.clients[client] {
		h.mu.Unlock()
		return
	}
	delete(h.clients, client)
	removeFromIndex(h.clientsByUser, client.UserID, client)
	for channel := range client.Channels {
		removeFromIndex(h.clientsByChan, channel, client)
		if strings.HasPrefix(channel, "chat:") &&
			!h.userPresentInChannelLocked(channel, client.UserID) {
			offlineChats = append(offlineChats, strings.TrimPrefix(channel, "chat:"))
		}
	}
	for chatID, state := range client.typing {
		if state.timer != nil {
			state.timer.Stop()
		}
		if !h.userTypingInChatLocked(client.UserID, chatID, client) {
			stoppedTyping = append(stoppedTyping, chatID)
		}
	}
	client.Channels = make(map[string]bool)
	client.typing = make(map[string]*typingState)
	client.closeOnce.Do(func() { close(client.done) })
	h.mu.Unlock()

	for _, chatID := range stoppedTyping {
		h.publishTyping(client.UserID, chatID, false, nil)
	}
	for _, chatID := range offlineChats {
		h.updatePresence(client.UserID, chatID, false)
	}
}

func (c *Client) readPump(h *Hub) {
	defer func() {
		h.unregisterClient(c)
		_ = c.Conn.Close()
	}()

	c.Conn.SetReadLimit(maxInboundFrameSize)
	_ = c.Conn.SetReadDeadline(time.Now().Add(h.pongWait))
	c.Conn.SetPongHandler(func(string) error {
		return c.Conn.SetReadDeadline(time.Now().Add(h.pongWait))
	})

	for {
		messageType, message, err := c.Conn.ReadMessage()
		if err != nil {
			return
		}
		if messageType != fiberws.TextMessage {
			writeClose(c.Conn, fiberws.CloseUnsupportedData, "text frames required", h.writeWait)
			return
		}
		if code, reason := h.handleInbound(c, message); code != 0 {
			writeClose(c.Conn, code, reason, h.writeWait)
			return
		}
	}
}

func (c *Client) writePump(h *Hub) {
	ticker := time.NewTicker(h.pingPeriod)
	defer func() {
		ticker.Stop()
		_ = c.Conn.Close()
	}()

	for {
		select {
		case message := <-c.Send:
			_ = c.Conn.SetWriteDeadline(time.Now().Add(h.writeWait))
			if err := c.Conn.WriteMessage(fiberws.TextMessage, message); err != nil {
				return
			}
		case request := <-c.closeRequest:
			writeClose(c.Conn, request.code, request.reason, h.writeWait)
			return
		case <-ticker.C:
			if err := c.Conn.WriteControl(
				fiberws.PingMessage,
				nil,
				time.Now().Add(h.writeWait),
			); err != nil {
				return
			}
		case <-c.done:
			return
		}
	}
}

func (c *Client) enqueue(data []byte) bool {
	select {
	case <-c.done:
		return false
	default:
	}
	select {
	case c.Send <- data:
		return true
	default:
		c.requestClose(fiberws.CloseTryAgainLater, "client backpressure")
		return false
	}
}

func (c *Client) requestClose(code int, reason string) {
	select {
	case c.closeRequest <- closeRequest{code: code, reason: reason}:
	default:
	}
}

func (h *Hub) handleInbound(client *Client, message []byte) (int, string) {
	if len(message) > maxInboundFrameSize {
		return fiberws.CloseMessageTooBig, "frame exceeds limit"
	}

	var incoming inboundEnvelope
	if err := decodeJSON(message, &incoming); err != nil {
		h.sendError(client, "", "invalid_envelope", "invalid realtime envelope", false)
		return 0, ""
	}
	if incoming.Event == "" {
		h.sendError(client, incoming.RequestID, "invalid_envelope", "event is required", false)
		return 0, ""
	}
	if len(incoming.Data) > maxInboundPayloadSize {
		return fiberws.CloseMessageTooBig, "payload exceeds limit"
	}

	switch incoming.Event {
	case "subscribe:post", "unsubscribe:post", "subscribe:user", "unsubscribe:user":
		h.handleLegacySubscription(client, incoming)
		return 0, ""
	}
	if incoming.Version != realtime.ProtocolVersion {
		h.sendError(
			client,
			incoming.RequestID,
			"unsupported_version",
			"realtime protocol version 1 is required",
			false,
		)
		return 0, ""
	}

	switch incoming.Event {
	case realtime.EventChatJoin:
		h.handleChatJoin(client, incoming)
	case realtime.EventChatLeave:
		h.handleChatLeave(client, incoming)
	case realtime.EventMessageSend:
		h.handleMessageSend(client, incoming)
	case realtime.EventMessageEdit:
		h.handleMessageEdit(client, incoming)
	case realtime.EventMessageDelete:
		h.handleMessageDelete(client, incoming)
	case realtime.EventMessageRead:
		h.handleMessageRead(client, incoming)
	case realtime.EventMessageReaction:
		h.handleMessageReaction(client, incoming)
	case realtime.EventUserTyping:
		h.handleTyping(client, incoming)
	default:
		h.sendError(
			client,
			incoming.RequestID,
			"unsupported_event",
			"event is not supported",
			false,
		)
	}
	return 0, ""
}

type inboundEnvelope struct {
	Version      string          `json:"version"`
	Event        string          `json:"event"`
	RequestID    string          `json:"request_id"`
	OperationKey string          `json:"operation_key"`
	Data         json.RawMessage `json:"data"`

	PostID  string `json:"postId"`
	Channel string `json:"channel"`
}

func (h *Hub) handleLegacySubscription(client *Client, incoming inboundEnvelope) {
	var channel string
	var subscribe bool
	switch incoming.Event {
	case "subscribe:post", "unsubscribe:post":
		postID, err := canonicalUUID(incoming.PostID)
		if err != nil {
			h.sendError(client, incoming.RequestID, "invalid_payload", "invalid resource identifier", false)
			return
		}
		channel = "post:" + postID
		subscribe = incoming.Event == "subscribe:post"
	case "subscribe:user", "unsubscribe:user":
		userID, err := canonicalUUID(incoming.Channel)
		if err != nil || userID != client.UserID {
			h.sendError(client, incoming.RequestID, "not_authorized", "operation is not authorized", false)
			return
		}
		channel = "user:" + userID
		subscribe = incoming.Event == "subscribe:user"
	}

	if subscribe {
		if !h.addSubscription(client, channel) {
			h.sendError(
				client,
				incoming.RequestID,
				"subscription_limit",
				"subscription limit reached",
				false,
			)
			return
		}
	} else {
		h.removeSubscription(client, channel)
	}
}

func (h *Hub) handleChatJoin(client *Client, incoming inboundEnvelope) {
	chatID, ok := h.chatIDPayload(client, incoming)
	if !ok {
		return
	}
	if h.gateway == nil {
		h.sendError(client, incoming.RequestID, "service_unavailable", "service unavailable", true)
		return
	}

	ctx, cancel := context.WithTimeout(h.ctx, h.operationWait)
	err := h.gateway.AuthorizeChat(ctx, client.UserID, chatID)
	cancel()
	if err != nil {
		h.sendGatewayError(client, incoming.RequestID, err)
		return
	}

	channel := "chat:" + chatID
	added, firstPresence, limitReached := h.joinChat(client, channel)
	if limitReached {
		h.sendError(client, incoming.RequestID, "subscription_limit", "subscription limit reached", false)
		return
	}
	if added && firstPresence {
		h.updatePresence(client.UserID, chatID, true)
	}
	h.sendAck(client, incoming.RequestID, incoming.Event, "", realtime.MutationAck{
		ResourceType: "chat",
		ResourceID:   chatID,
	})
}

func (h *Hub) handleChatLeave(client *Client, incoming inboundEnvelope) {
	chatID, ok := h.chatIDPayload(client, incoming)
	if !ok {
		return
	}
	removed, lastPresence, typingStopped := h.leaveChat(client, "chat:"+chatID)
	if typingStopped {
		h.publishTyping(client.UserID, chatID, false, nil)
	}
	if removed && lastPresence {
		h.updatePresence(client.UserID, chatID, false)
	}
	h.sendAck(client, incoming.RequestID, incoming.Event, "", realtime.MutationAck{
		ResourceType: "chat",
		ResourceID:   chatID,
	})
}

func (h *Hub) handleMessageSend(client *Client, incoming inboundEnvelope) {
	var payload realtime.MessageSendPayload
	if !h.decodePayload(client, incoming, &payload) {
		return
	}
	if payload.ChatID == "" {
		payload.ChatID = payload.LegacyChatID
	}
	if payload.MediaURL == nil {
		payload.MediaURL = payload.LegacyMediaURL
	}
	if payload.ReplyToID == nil {
		payload.ReplyToID = payload.LegacyReplyToID
	}
	chatID, err := canonicalUUID(payload.ChatID)
	if err != nil {
		h.sendError(client, incoming.RequestID, "invalid_payload", "invalid resource identifier", false)
		return
	}
	payload.ChatID = chatID
	if !h.isSubscribed(client, "chat:"+chatID) {
		h.sendError(client, incoming.RequestID, "not_authorized", "operation is not authorized", false)
		return
	}

	operationKey := strings.TrimSpace(incoming.OperationKey)
	payloadKey := strings.TrimSpace(payload.OperationKey)
	if payloadKey == "" {
		payloadKey = strings.TrimSpace(payload.LegacyOperationKey)
	}
	if operationKey == "" {
		operationKey = payloadKey
	} else if payloadKey != "" && payloadKey != operationKey {
		h.sendError(
			client,
			incoming.RequestID,
			"idempotency_conflict",
			"operation key fields do not match",
			false,
		)
		return
	}
	if h.gateway == nil {
		h.sendError(client, incoming.RequestID, "service_unavailable", "service unavailable", true)
		return
	}

	ctx, cancel := context.WithTimeout(h.ctx, h.operationWait)
	ack, err := h.gateway.SendMessage(ctx, client.UserID, operationKey, payload)
	cancel()
	if err != nil {
		h.sendGatewayError(client, incoming.RequestID, err)
		return
	}
	h.sendAck(client, incoming.RequestID, incoming.Event, operationKey, ack)
}

func (h *Hub) handleMessageEdit(client *Client, incoming inboundEnvelope) {
	var payload realtime.MessageEditCommand
	if !h.decodePayload(client, incoming, &payload) {
		return
	}
	if payload.MessageID == "" {
		payload.MessageID = payload.LegacyMessageID
	}
	if !h.canonicalMessageID(client, incoming.RequestID, &payload.MessageID) {
		return
	}
	h.runMutation(client, incoming, func(ctx context.Context) (realtime.MutationAck, error) {
		return h.gateway.EditMessage(ctx, client.UserID, payload)
	})
}

func (h *Hub) handleMessageDelete(client *Client, incoming inboundEnvelope) {
	var payload realtime.MessageDeleteCommand
	if !h.decodePayload(client, incoming, &payload) {
		return
	}
	if payload.MessageID == "" {
		payload.MessageID = payload.LegacyMessageID
	}
	if !h.canonicalMessageID(client, incoming.RequestID, &payload.MessageID) {
		return
	}
	h.runMutation(client, incoming, func(ctx context.Context) (realtime.MutationAck, error) {
		return h.gateway.DeleteMessage(ctx, client.UserID, payload)
	})
}

func (h *Hub) handleMessageRead(client *Client, incoming inboundEnvelope) {
	var payload realtime.MessageReadCommand
	if !h.decodePayload(client, incoming, &payload) {
		return
	}
	if payload.MessageID == "" {
		payload.MessageID = payload.LegacyMessageID
	}
	if !h.canonicalMessageID(client, incoming.RequestID, &payload.MessageID) {
		return
	}
	h.runMutation(client, incoming, func(ctx context.Context) (realtime.MutationAck, error) {
		return h.gateway.ReadMessage(ctx, client.UserID, payload)
	})
}

func (h *Hub) handleMessageReaction(client *Client, incoming inboundEnvelope) {
	var payload realtime.MessageReactionCommand
	if !h.decodePayload(client, incoming, &payload) {
		return
	}
	if payload.MessageID == "" {
		payload.MessageID = payload.LegacyMessageID
	}
	if !h.canonicalMessageID(client, incoming.RequestID, &payload.MessageID) {
		return
	}
	h.runMutation(client, incoming, func(ctx context.Context) (realtime.MutationAck, error) {
		return h.gateway.ReactToMessage(ctx, client.UserID, payload)
	})
}

func (h *Hub) runMutation(
	client *Client,
	incoming inboundEnvelope,
	mutate func(context.Context) (realtime.MutationAck, error),
) {
	if h.gateway == nil {
		h.sendError(client, incoming.RequestID, "service_unavailable", "service unavailable", true)
		return
	}
	ctx, cancel := context.WithTimeout(h.ctx, h.operationWait)
	ack, err := mutate(ctx)
	cancel()
	if err != nil {
		h.sendGatewayError(client, incoming.RequestID, err)
		return
	}
	h.sendAck(client, incoming.RequestID, incoming.Event, "", ack)
}

func (h *Hub) handleTyping(client *Client, incoming inboundEnvelope) {
	chatID, ok := h.chatIDPayload(client, incoming)
	if !ok {
		return
	}
	channel := "chat:" + chatID
	now := time.Now().UTC()
	h.mu.RLock()
	joined := client.Channels[channel]
	state := client.typing[chatID]
	needsAuthorization := state == nil ||
		now.Sub(state.lastAuthorized) >= h.typingInterval
	h.mu.RUnlock()
	if !joined {
		h.sendError(client, incoming.RequestID, "not_authorized", "operation is not authorized", false)
		return
	}
	if needsAuthorization && h.gateway == nil {
		h.sendError(client, incoming.RequestID, "service_unavailable", "service unavailable", true)
		return
	}
	if needsAuthorization {
		ctx, cancel := context.WithTimeout(h.ctx, h.operationWait)
		err := h.gateway.AuthorizeChat(ctx, client.UserID, chatID)
		cancel()
		if err != nil {
			h.sendGatewayError(client, incoming.RequestID, err)
			return
		}
	}

	expiresAt := now.Add(h.typingTTL)
	shouldPublish := false
	h.mu.Lock()
	if !client.Channels[channel] {
		h.mu.Unlock()
		h.sendError(client, incoming.RequestID, "not_authorized", "operation is not authorized", false)
		return
	}
	state = client.typing[chatID]
	if state == nil {
		state = &typingState{}
		client.typing[chatID] = state
	}
	if needsAuthorization {
		state.lastAuthorized = now
	}
	if state.lastSent.IsZero() || now.Sub(state.lastSent) >= h.typingInterval {
		state.lastSent = now
		shouldPublish = true
	}
	state.lastActivity = now
	if state.timer != nil {
		state.timer.Stop()
	}
	state.timer = time.AfterFunc(h.typingTTL, func() {
		h.expireTyping(client, chatID, now)
	})
	h.mu.Unlock()

	if shouldPublish {
		h.publishTyping(client.UserID, chatID, true, &expiresAt)
	}
	h.sendAck(client, incoming.RequestID, incoming.Event, "", realtime.MutationAck{
		ResourceType: "chat",
		ResourceID:   chatID,
	})
}

func (h *Hub) expireTyping(client *Client, chatID string, activity time.Time) {
	h.mu.Lock()
	state := client.typing[chatID]
	if state == nil || !state.lastActivity.Equal(activity) {
		h.mu.Unlock()
		return
	}
	delete(client.typing, chatID)
	joined := client.Channels["chat:"+chatID]
	h.mu.Unlock()
	if joined {
		h.publishTyping(client.UserID, chatID, false, nil)
	}
}

func (h *Hub) chatIDPayload(client *Client, incoming inboundEnvelope) (string, bool) {
	var payload struct {
		ChatID       string `json:"chat_id"`
		LegacyChatID string `json:"chatId"`
	}
	if !h.decodePayload(client, incoming, &payload) {
		return "", false
	}
	chatID := payload.ChatID
	if chatID == "" {
		chatID = payload.LegacyChatID
	}
	canonical, err := canonicalUUID(chatID)
	if err != nil {
		h.sendError(client, incoming.RequestID, "invalid_payload", "invalid resource identifier", false)
		return "", false
	}
	return canonical, true
}

func (h *Hub) decodePayload(
	client *Client,
	incoming inboundEnvelope,
	target any,
) bool {
	if len(incoming.Data) == 0 || string(incoming.Data) == "null" {
		h.sendError(client, incoming.RequestID, "invalid_payload", "event payload is required", false)
		return false
	}
	if err := decodeJSON(incoming.Data, target); err != nil {
		h.sendError(client, incoming.RequestID, "invalid_payload", "invalid event payload", false)
		return false
	}
	return true
}

func (h *Hub) canonicalMessageID(client *Client, requestID string, messageID *string) bool {
	canonical, err := canonicalUUID(*messageID)
	if err != nil {
		h.sendError(client, requestID, "invalid_payload", "invalid resource identifier", false)
		return false
	}
	*messageID = canonical
	return true
}

func (h *Hub) joinChat(client *Client, channel string) (bool, bool, bool) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if client.Channels[channel] {
		return false, false, false
	}
	if len(client.Channels) >= maxSubscriptionsPerClient {
		return false, false, true
	}
	firstPresence := !h.userPresentInChannelLocked(channel, client.UserID)
	client.Channels[channel] = true
	addToIndex(h.clientsByChan, channel, client)
	return true, firstPresence, false
}

func (h *Hub) leaveChat(client *Client, channel string) (bool, bool, bool) {
	h.mu.Lock()
	defer h.mu.Unlock()
	if !client.Channels[channel] {
		return false, false, false
	}
	delete(client.Channels, channel)
	removeFromIndex(h.clientsByChan, channel, client)
	chatID := strings.TrimPrefix(channel, "chat:")
	typingStopped := false
	if state := client.typing[chatID]; state != nil {
		if state.timer != nil {
			state.timer.Stop()
		}
		delete(client.typing, chatID)
		typingStopped = !h.userTypingInChatLocked(client.UserID, chatID, client)
	}
	return true, !h.userPresentInChannelLocked(channel, client.UserID), typingStopped
}

func (h *Hub) addSubscription(client *Client, channel string) bool {
	h.mu.Lock()
	defer h.mu.Unlock()
	if client.Channels[channel] {
		return true
	}
	if len(client.Channels) >= maxSubscriptionsPerClient {
		return false
	}
	client.Channels[channel] = true
	addToIndex(h.clientsByChan, channel, client)
	return true
}

func (h *Hub) removeSubscription(client *Client, channel string) {
	h.mu.Lock()
	defer h.mu.Unlock()
	delete(client.Channels, channel)
	removeFromIndex(h.clientsByChan, channel, client)
}

func (h *Hub) isSubscribed(client *Client, channel string) bool {
	h.mu.RLock()
	defer h.mu.RUnlock()
	return client.Channels[channel]
}

func (h *Hub) userPresentInChannelLocked(channel string, userID string) bool {
	for client := range h.clientsByChan[channel] {
		if client.UserID == userID {
			return true
		}
	}
	return false
}

func (h *Hub) userTypingInChatLocked(userID string, chatID string, except *Client) bool {
	for client := range h.clientsByUser[userID] {
		if client != except && client.typing[chatID] != nil {
			return true
		}
	}
	return false
}

func (h *Hub) sendAck(
	client *Client,
	requestID string,
	event string,
	operationKey string,
	ack realtime.MutationAck,
) {
	envelope, err := realtime.NewAck(
		requestID,
		event,
		operationKey,
		ack.ResourceType,
		ack.ResourceID,
		ack.Replayed,
	)
	if err != nil {
		slog.Error("build realtime acknowledgement", "error", err)
		return
	}
	h.sendEnvelope(client, envelope)
}

func (h *Hub) sendError(
	client *Client,
	requestID string,
	code string,
	message string,
	retryable bool,
) {
	h.sendEnvelope(client, realtime.NewError(requestID, code, message, retryable))
}

func (h *Hub) sendGatewayError(client *Client, requestID string, err error) {
	var gatewayErr *realtime.GatewayError
	if errors.As(err, &gatewayErr) {
		h.sendError(
			client,
			requestID,
			gatewayErr.Code,
			gatewayErr.Message,
			gatewayErr.Retryable,
		)
		return
	}
	slog.Error("unclassified realtime gateway error", "error", err)
	h.sendError(client, requestID, "internal_error", "internal server error", false)
}

func (h *Hub) sendEnvelope(client *Client, envelope realtime.Envelope) {
	data, err := json.Marshal(envelope)
	if err != nil {
		slog.Error("marshal realtime envelope", "error", err)
		return
	}
	client.enqueue(data)
}

func (h *Hub) publishTyping(
	userID string,
	chatID string,
	typing bool,
	expiresAt *time.Time,
) {
	h.publish("chat:"+chatID, realtime.EventUserTyping, realtime.TypingPayload{
		ChatID:    chatID,
		UserID:    userID,
		Typing:    typing,
		ExpiresAt: expiresAt,
	})
}

func (h *Hub) updatePresence(userID string, chatID string, online bool) {
	h.presenceMu.Lock()
	defer h.presenceMu.Unlock()
	key := chatID + ":" + userID
	if online {
		h.mu.Lock()
		state := h.presence[key]
		if state == nil {
			state = &presenceState{}
			h.presence[key] = state
		}
		if state.timer != nil {
			state.timer.Stop()
			state.timer = nil
		}
		alreadyOnline := state.online
		state.online = true
		h.mu.Unlock()
		if !alreadyOnline {
			h.publishPresence(userID, chatID, true)
		}
		return
	}

	h.mu.Lock()
	state := h.presence[key]
	if state == nil || !state.online {
		h.mu.Unlock()
		return
	}
	if state.timer != nil {
		state.timer.Stop()
	}
	state.timer = time.AfterFunc(h.presenceDebounce, func() {
		h.expirePresence(key, userID, chatID)
	})
	h.mu.Unlock()
}

func (h *Hub) expirePresence(key string, userID string, chatID string) {
	h.presenceMu.Lock()
	defer h.presenceMu.Unlock()
	h.mu.Lock()
	state := h.presence[key]
	if state == nil || h.userPresentInChannelLocked("chat:"+chatID, userID) {
		h.mu.Unlock()
		return
	}
	delete(h.presence, key)
	h.mu.Unlock()
	h.publishPresence(userID, chatID, false)
}

func (h *Hub) publishPresence(userID string, chatID string, online bool) {
	event := realtime.EventUserOffline
	if online {
		event = realtime.EventUserOnline
	}
	h.publish("chat:"+chatID, event, realtime.PresencePayload{
		ChatID:    chatID,
		UserID:    userID,
		Online:    online,
		ChangedAt: time.Now().UTC(),
	})
}

func (h *Hub) publish(channel string, event string, payload any) {
	envelope, err := realtime.NewEnvelope(event, payload)
	if err != nil {
		slog.Error("build ephemeral realtime event", "event", event, "error", err)
		return
	}
	ctx, cancel := context.WithTimeout(h.ctx, 2*time.Second)
	defer cancel()
	if err := h.publisher.Publish(ctx, realtime.Delivery{
		Channel:  channel,
		Envelope: envelope,
	}); err != nil {
		slog.Error("publish ephemeral realtime event", "event", event, "error", err)
	}
}

// Broadcast preserves existing post/user producers while routing through Redis
// when available. Existing unversioned payloads remain valid for those channels.
func (h *Hub) Broadcast(channel string, message Message) {
	data, err := json.Marshal(message)
	if err != nil {
		slog.Error("marshal legacy websocket message", "error", err)
		return
	}
	if h.rdb == nil {
		h.localFanout(channel, data)
		return
	}
	ctx, cancel := context.WithTimeout(h.ctx, 3*time.Second)
	defer cancel()
	if err := h.rdb.Publish(ctx, "ws:"+channel, data).Err(); err != nil {
		slog.Error("publish legacy websocket message", "error", err)
	}
}

func (h *Hub) BroadcastToUser(userID string, message Message) {
	data, err := json.Marshal(message)
	if err != nil {
		slog.Error("marshal user websocket message", "error", err)
		return
	}
	if h.rdb == nil {
		h.localFanout("user:"+userID, data)
		return
	}
	ctx, cancel := context.WithTimeout(h.ctx, 3*time.Second)
	defer cancel()
	if err := h.rdb.Publish(ctx, "ws:user:"+userID, data).Err(); err != nil {
		slog.Error("publish user websocket message", "error", err)
	}
}

func (h *Hub) ListenRedis(pattern string) {
	if err := h.ListenRedisContext(h.ctx, pattern); err != nil &&
		!errors.Is(err, context.Canceled) {
		slog.Error("websocket Redis listener stopped", "pattern", pattern, "error", err)
	}
}

func (h *Hub) ListenRedisContext(ctx context.Context, pattern string) error {
	if h.rdb == nil {
		return errors.New("websocket Redis listener is unavailable")
	}
	pubsub := h.rdb.PSubscribe(ctx, "ws:"+pattern)
	defer pubsub.Close()
	if _, err := pubsub.Receive(ctx); err != nil {
		return err
	}

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case message, ok := <-pubsub.Channel():
			if !ok {
				return nil
			}
			channel := strings.TrimPrefix(message.Channel, "ws:")
			h.localFanout(channel, []byte(message.Payload))
		}
	}
}

func (h *Hub) localFanout(channel string, data []byte) {
	h.mu.RLock()
	seen := make(map[*Client]bool)
	for client := range h.clientsByChan[channel] {
		seen[client] = true
	}
	if strings.HasPrefix(channel, "user:") {
		for client := range h.clientsByUser[strings.TrimPrefix(channel, "user:")] {
			seen[client] = true
		}
	}
	clients := copyClients(seen)
	h.mu.RUnlock()
	for _, client := range clients {
		client.enqueue(data)
	}
}

func (h *Hub) Close() {
	h.cancel()
	h.mu.Lock()
	clients := copyClients(h.clients)
	for _, state := range h.presence {
		if state.timer != nil {
			state.timer.Stop()
		}
	}
	h.mu.Unlock()
	for _, client := range clients {
		client.requestClose(fiberws.CloseGoingAway, "server shutting down")
	}
}

func decodeJSON(data []byte, target any) error {
	decoder := json.NewDecoder(bytes.NewReader(data))
	if err := decoder.Decode(target); err != nil {
		return err
	}
	if err := decoder.Decode(&struct{}{}); !errors.Is(err, io.EOF) {
		if err == nil {
			return errors.New("multiple JSON values")
		}
		return err
	}
	return nil
}

func canonicalUUID(value string) (string, error) {
	value = strings.TrimSpace(value)
	id, err := uuid.Parse(value)
	if err != nil || !strings.EqualFold(value, id.String()) {
		return "", errors.New("invalid UUID")
	}
	return id.String(), nil
}

func writeClose(conn websocketConn, code int, reason string, wait time.Duration) {
	_ = conn.WriteControl(
		fiberws.CloseMessage,
		fiberws.FormatCloseMessage(code, reason),
		time.Now().Add(wait),
	)
}

func copyClients(source map[*Client]bool) []*Client {
	clients := make([]*Client, 0, len(source))
	for client := range source {
		clients = append(clients, client)
	}
	return clients
}

func addToIndex(index map[string]map[*Client]bool, key string, client *Client) {
	if index[key] == nil {
		index[key] = make(map[*Client]bool)
	}
	index[key][client] = true
}

func removeFromIndex(index map[string]map[*Client]bool, key string, client *Client) {
	if clients := index[key]; clients != nil {
		delete(clients, client)
		if len(clients) == 0 {
			delete(index, key)
		}
	}
}
