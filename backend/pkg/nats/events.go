package nats

import (
	"encoding/json"

	"github.com/nats-io/nats.go"
)

type EventPublisher struct {
	nc *nats.Conn
}

func NewEventPublisher(nc *nats.Conn) *EventPublisher {
	return &EventPublisher{nc: nc}
}

func (p *EventPublisher) Publish(subject string, payload interface{}) error {
	data, err := json.Marshal(payload)
	if err != nil {
		return err
	}
	return p.nc.Publish(subject, data)
}

type PostPublishedEvent struct {
	PostID   string   `json:"post_id"`
	AuthorID string   `json:"author_id"`
	Title    string   `json:"title"`
	Tags     []string `json:"tags"`
	Category string   `json:"category"`
}

type CommentCreatedEvent struct {
	CommentID string `json:"comment_id"`
	PostID    string `json:"post_id"`
	AuthorID  string `json:"author_id"`
	ParentID  string `json:"parent_id,omitempty"`
}

type NotificationEvent struct {
	UserID string      `json:"user_id"`
	Type   string      `json:"type"`
	Title  string      `json:"title"`
	Body   string      `json:"body"`
	Data   interface{} `json:"data"`
}
