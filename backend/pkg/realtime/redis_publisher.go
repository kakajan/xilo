package realtime

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/redis/go-redis/v9"
)

const redisChannelPrefix = "ws:"

type RedisPublisher struct {
	client redisPublishClient
}

type redisPublishClient interface {
	Publish(ctx context.Context, channel string, message interface{}) *redis.IntCmd
}

func NewRedisPublisher(client redisPublishClient) *RedisPublisher {
	return &RedisPublisher{client: client}
}

func (p *RedisPublisher) Publish(ctx context.Context, delivery Delivery) error {
	if p == nil || p.client == nil {
		return fmt.Errorf("realtime Redis publisher is unavailable")
	}
	if !validFanoutChannel(delivery.Channel) {
		return fmt.Errorf("invalid realtime fanout channel")
	}
	if delivery.Envelope.Version != ProtocolVersion || delivery.Envelope.Event == "" {
		return fmt.Errorf("invalid realtime envelope")
	}
	data, err := json.Marshal(delivery.Envelope)
	if err != nil {
		return fmt.Errorf("marshal realtime envelope: %w", err)
	}
	if err := p.client.Publish(ctx, redisChannelPrefix+delivery.Channel, data).Err(); err != nil {
		return fmt.Errorf("publish realtime envelope: %w", err)
	}
	return nil
}

func validFanoutChannel(channel string) bool {
	if strings.HasPrefix(channel, "chat:") {
		return len(channel) > len("chat:")
	}
	if strings.HasPrefix(channel, "user:") {
		return len(channel) > len("user:")
	}
	if strings.HasPrefix(channel, "post:") {
		return len(channel) > len("post:")
	}
	return false
}
