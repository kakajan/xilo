package realtime

import (
	"context"
	"encoding/json"
	"testing"

	"github.com/redis/go-redis/v9"
)

type fakeRedisPublishClient struct {
	channel string
	message interface{}
	calls   int
}

func (c *fakeRedisPublishClient) Publish(
	_ context.Context,
	channel string,
	message interface{},
) *redis.IntCmd {
	c.channel = channel
	c.message = message
	c.calls++
	return redis.NewIntResult(1, nil)
}

func TestRedisPublisherUsesTypedNamespacedEnvelope(t *testing.T) {
	client := &fakeRedisPublishClient{}
	publisher := NewRedisPublisher(client)
	envelope, err := NewEnvelope(EventMessageRead, MessageReadPayload{
		MessageID: "11111111-1111-4111-8111-111111111111",
		ChatID:    "22222222-2222-4222-8222-222222222222",
		UserID:    "33333333-3333-4333-8333-333333333333",
	})
	if err != nil {
		t.Fatalf("NewEnvelope returned error: %v", err)
	}

	if err := publisher.Publish(context.Background(), Delivery{
		Channel:  "chat:22222222-2222-4222-8222-222222222222",
		Envelope: envelope,
	}); err != nil {
		t.Fatalf("Publish returned error: %v", err)
	}
	if client.channel != "ws:chat:22222222-2222-4222-8222-222222222222" {
		t.Fatalf("channel = %q", client.channel)
	}
	data, ok := client.message.([]byte)
	if !ok {
		t.Fatalf("message type = %T, want []byte", client.message)
	}
	var published Envelope
	if err := json.Unmarshal(data, &published); err != nil {
		t.Fatalf("decode published envelope: %v", err)
	}
	if published.Version != ProtocolVersion || published.Event != EventMessageRead ||
		published.EventID == "" {
		t.Fatalf("unexpected published envelope: %+v", published)
	}
}

func TestRedisPublisherRejectsUnscopedChannel(t *testing.T) {
	client := &fakeRedisPublishClient{}
	publisher := NewRedisPublisher(client)
	envelope, err := NewEnvelope(EventMessageRead, struct{}{})
	if err != nil {
		t.Fatalf("NewEnvelope returned error: %v", err)
	}

	if err := publisher.Publish(context.Background(), Delivery{
		Channel:  "arbitrary-channel",
		Envelope: envelope,
	}); err == nil {
		t.Fatal("expected invalid channel error")
	}
	if client.calls != 0 {
		t.Fatal("invalid delivery reached Redis")
	}
}
