package websocket

import (
	"context"
	"encoding/json"
	"log/slog"
	"sync"
	"time"

	"github.com/gofiber/contrib/websocket"
	"github.com/redis/go-redis/v9"
	"github.com/xilo-platform/xilo/pkg/jwt"
)

const (
	maxSubscriptionsPerClient = 50
	maxConnectedClients       = 10_000
	clientSendBufferSize      = 256
)

type Message struct {
	Event string          `json:"event"`
	Data  json.RawMessage `json:"data"`
}

type Client struct {
	Conn     *websocket.Conn
	UserID   string
	Send     chan []byte
	Channels map[string]bool
	mu       sync.Mutex
}

type Hub struct {
	clients       map[*Client]bool
	clientsByUser map[string]map[*Client]bool
	clientsByChan map[string]map[*Client]bool
	register      chan *Client
	unregister    chan *Client
	mu            sync.RWMutex
	rdb           *redis.Client
	jwtMgr        *jwt.Manager
}

func NewHub(rdb *redis.Client, jwtMgr *jwt.Manager) *Hub {
	h := &Hub{
		clients:       make(map[*Client]bool),
		clientsByUser: make(map[string]map[*Client]bool),
		clientsByChan: make(map[string]map[*Client]bool),
		register:      make(chan *Client),
		unregister:    make(chan *Client),
		rdb:           rdb,
		jwtMgr:        jwtMgr,
	}
	go h.run()
	return h
}

func (h *Hub) run() {
	for {
		select {
		case client := <-h.register:
			h.mu.Lock()
			if len(h.clients) >= maxConnectedClients {
				h.mu.Unlock()
				client.Conn.Close()
				continue
			}
			h.clients[client] = true
			addToIndex(h.clientsByUser, client.UserID, client)
			h.mu.Unlock()
		case client := <-h.unregister:
			h.mu.Lock()
			if _, ok := h.clients[client]; ok {
				delete(h.clients, client)
				removeFromIndex(h.clientsByUser, client.UserID, client)
				client.mu.Lock()
				for ch := range client.Channels {
					removeFromIndex(h.clientsByChan, ch, client)
				}
				client.mu.Unlock()
				close(client.Send)
			}
			h.mu.Unlock()
		}
	}
}

func (h *Hub) Broadcast(channel string, msg Message) {
	data, err := json.Marshal(msg)
	if err != nil {
		slog.Error("ws marshal failed", "error", err)
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	h.rdb.Publish(ctx, "ws:"+channel, data)

	h.mu.RLock()
	clients := h.clientsByChan[channel]
	h.mu.RUnlock()

	for client := range clients {
		select {
		case client.Send <- data:
		default:
		}
	}
}

func (h *Hub) BroadcastToUser(userID string, msg Message) {
	data, err := json.Marshal(msg)
	if err != nil {
		slog.Error("ws marshal failed", "error", err)
		return
	}

	h.mu.RLock()
	clients := h.clientsByUser[userID]
	h.mu.RUnlock()

	for client := range clients {
		select {
		case client.Send <- data:
		default:
		}
	}
}

func (h *Hub) ListenRedis(pattern string) {
	pubsub := h.rdb.PSubscribe(context.Background(), "ws:"+pattern)
	defer pubsub.Close()

	ch := pubsub.Channel()
	for msg := range ch {
		channel := msg.Channel
		if len(channel) > 3 {
			channel = channel[3:]
		}

		h.mu.RLock()
		clients := h.clientsByChan[channel]
		h.mu.RUnlock()

		for client := range clients {
			select {
			case client.Send <- []byte(msg.Payload):
			default:
			}
		}
	}
}

func (h *Hub) HandleWebSocket(c *websocket.Conn) {
	token := extractWSToken(c)
	if token == "" {
		c.Close()
		return
	}

	claims, err := h.jwtMgr.ValidateToken(token)
	if err != nil {
		c.Close()
		return
	}

	client := &Client{
		Conn:     c,
		UserID:   claims.UserID,
		Send:     make(chan []byte, clientSendBufferSize),
		Channels: make(map[string]bool),
	}

	h.register <- client

	go client.writePump()
	client.readPump(h)
}

func extractWSToken(c *websocket.Conn) string {
	token := c.Query("token")
	if token != "" {
		return token
	}

	token = c.Cookies("xilo_access_token")
	if token != "" {
		return token
	}

	return ""
}

func (c *Client) readPump(hub *Hub) {
	defer func() {
		hub.unregister <- c
		c.Conn.Close()
	}()

	for {
		_, msg, err := c.Conn.ReadMessage()
		if err != nil {
			break
		}

		var wsMsg struct {
			Event   string `json:"event"`
			PostID  string `json:"postId"`
			Channel string `json:"channel"`
		}
		if err := json.Unmarshal(msg, &wsMsg); err != nil {
			continue
		}

		hub.mu.Lock()
		switch wsMsg.Event {
		case "subscribe:post":
			ch := "post:" + wsMsg.PostID
			c.mu.Lock()
			if len(c.Channels) < maxSubscriptionsPerClient {
				c.Channels[ch] = true
				addToIndex(hub.clientsByChan, ch, c)
			}
			c.mu.Unlock()
		case "unsubscribe:post":
			ch := "post:" + wsMsg.PostID
			c.mu.Lock()
			delete(c.Channels, ch)
			removeFromIndex(hub.clientsByChan, ch, c)
			c.mu.Unlock()
		case "subscribe:user":
			ch := "user:" + wsMsg.Channel
			c.mu.Lock()
			if len(c.Channels) < maxSubscriptionsPerClient {
				c.Channels[ch] = true
				addToIndex(hub.clientsByChan, ch, c)
			}
			c.mu.Unlock()
		}
		hub.mu.Unlock()
	}
}

func (c *Client) writePump() {
	defer c.Conn.Close()
	for msg := range c.Send {
		if err := c.Conn.WriteMessage(websocket.TextMessage, msg); err != nil {
			break
		}
	}
}

func addToIndex(m map[string]map[*Client]bool, key string, client *Client) {
	if m[key] == nil {
		m[key] = make(map[*Client]bool)
	}
	m[key][client] = true
}

func removeFromIndex(m map[string]map[*Client]bool, key string, client *Client) {
	if clients, ok := m[key]; ok {
		delete(clients, client)
		if len(clients) == 0 {
			delete(m, key)
		}
	}
}
