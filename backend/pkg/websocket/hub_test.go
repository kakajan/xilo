package websocket

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"sync"
	"testing"
	"time"

	fiberws "github.com/gofiber/contrib/websocket"
	"github.com/xilo-platform/xilo/pkg/realtime"
)

const (
	wsTestUser  = "11111111-1111-4111-8111-111111111111"
	wsOtherUser = "22222222-2222-4222-8222-222222222222"
	wsTestChat  = "33333333-3333-4333-8333-333333333333"
	wsTestMsg   = "44444444-4444-4444-8444-444444444444"
	wsTestKey   = "55555555-5555-4555-8555-555555555555"
)

type fakePublisher struct {
	mu         sync.Mutex
	deliveries []realtime.Delivery
}

func (p *fakePublisher) Publish(_ context.Context, delivery realtime.Delivery) error {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.deliveries = append(p.deliveries, delivery)
	return nil
}

func (p *fakePublisher) snapshot() []realtime.Delivery {
	p.mu.Lock()
	defer p.mu.Unlock()
	return append([]realtime.Delivery(nil), p.deliveries...)
}

type fakeChatGateway struct {
	authorizeErr   error
	authorizeCalls int
	sendAck        realtime.MutationAck
	sendErr        error
	sendCalls      int
	operationKey   string
	sendPayload    realtime.MessageSendPayload
}

func (g *fakeChatGateway) AuthorizeChat(context.Context, string, string) error {
	g.authorizeCalls++
	return g.authorizeErr
}

func (g *fakeChatGateway) SendMessage(
	_ context.Context,
	_ string,
	operationKey string,
	payload realtime.MessageSendPayload,
) (realtime.MutationAck, error) {
	g.sendCalls++
	g.operationKey = operationKey
	g.sendPayload = payload
	return g.sendAck, g.sendErr
}

func (g *fakeChatGateway) EditMessage(
	context.Context,
	string,
	realtime.MessageEditCommand,
) (realtime.MutationAck, error) {
	return realtime.MutationAck{}, nil
}

func (g *fakeChatGateway) DeleteMessage(
	context.Context,
	string,
	realtime.MessageDeleteCommand,
) (realtime.MutationAck, error) {
	return realtime.MutationAck{}, nil
}

func (g *fakeChatGateway) ReadMessage(
	context.Context,
	string,
	realtime.MessageReadCommand,
) (realtime.MutationAck, error) {
	return realtime.MutationAck{}, nil
}

func (g *fakeChatGateway) ReactToMessage(
	context.Context,
	string,
	realtime.MessageReactionCommand,
) (realtime.MutationAck, error) {
	return realtime.MutationAck{}, nil
}

func TestUnauthorizedChatJoinDoesNotSubscribe(t *testing.T) {
	gateway := &fakeChatGateway{
		authorizeErr: realtime.NewGatewayError(
			"not_authorized",
			"operation is not authorized",
			false,
			errors.New("not a member"),
		),
	}
	hub := NewHubWithDependencies(nil, nil, gateway, &fakePublisher{})
	client := newClient(nil, wsTestUser, "reader")
	if !hub.registerClient(client) {
		t.Fatal("register client")
	}
	defer hub.unregisterClient(client)

	hub.handleInbound(client, eventJSON(t, realtime.EventChatJoin, "", map[string]any{
		"chat_id": wsTestChat,
	}))

	envelope := nextEnvelope(t, client)
	if envelope.Event != realtime.EventError || envelope.Error == nil ||
		envelope.Error.Code != "not_authorized" {
		t.Fatalf("unexpected error envelope: %+v", envelope)
	}
	if hub.isSubscribed(client, "chat:"+wsTestChat) {
		t.Fatal("unauthorized client was subscribed")
	}
}

func TestUserSubscriptionOnlyAllowsAuthenticatedUser(t *testing.T) {
	hub := NewHubWithDependencies(nil, nil, nil, &fakePublisher{})
	client := newClient(nil, wsTestUser, "reader")
	if !hub.registerClient(client) {
		t.Fatal("register client")
	}
	defer hub.unregisterClient(client)

	message := []byte(`{"event":"subscribe:user","channel":"` + wsOtherUser + `"}`)
	hub.handleInbound(client, message)

	envelope := nextEnvelope(t, client)
	if envelope.Event != realtime.EventError || envelope.Error == nil ||
		envelope.Error.Code != "not_authorized" {
		t.Fatalf("unexpected error envelope: %+v", envelope)
	}
	if hub.isSubscribed(client, "user:"+wsOtherUser) {
		t.Fatal("client subscribed to another user's channel")
	}
}

func TestMessageSendReplayReturnsOperationAck(t *testing.T) {
	gateway := &fakeChatGateway{
		sendAck: realtime.MutationAck{
			ResourceType: "message",
			ResourceID:   wsTestMsg,
			Replayed:     true,
		},
	}
	hub := NewHubWithDependencies(nil, nil, gateway, &fakePublisher{})
	client := newClient(nil, wsTestUser, "reader")
	if !hub.registerClient(client) {
		t.Fatal("register client")
	}
	defer hub.unregisterClient(client)
	if !hub.addSubscription(client, "chat:"+wsTestChat) {
		t.Fatal("join test chat")
	}

	content := "hello"
	hub.handleInbound(client, eventJSON(t, realtime.EventMessageSend, wsTestKey, map[string]any{
		"chat_id": wsTestChat,
		"type":    "text",
		"content": content,
	}))

	envelope := nextEnvelope(t, client)
	if envelope.Event != realtime.EventAck {
		t.Fatalf("event = %q, want ack", envelope.Event)
	}
	var ack realtime.AckPayload
	if err := json.Unmarshal(envelope.Data, &ack); err != nil {
		t.Fatalf("decode ack: %v", err)
	}
	if !ack.Replayed || ack.OperationKey != wsTestKey || ack.ResourceID != wsTestMsg {
		t.Fatalf("unexpected ack: %+v", ack)
	}
	if gateway.sendCalls != 1 || gateway.operationKey != wsTestKey ||
		gateway.sendPayload.ChatID != wsTestChat {
		t.Fatalf("unexpected gateway call: %+v", gateway)
	}
}

func TestTypingIsDebouncedPerJoinedChat(t *testing.T) {
	publisher := &fakePublisher{}
	gateway := &fakeChatGateway{}
	hub := NewHubWithDependencies(nil, nil, gateway, publisher)
	hub.typingInterval = time.Hour
	hub.typingTTL = time.Hour
	client := newClient(nil, wsTestUser, "reader")
	if !hub.registerClient(client) {
		t.Fatal("register client")
	}
	defer hub.unregisterClient(client)
	if !hub.addSubscription(client, "chat:"+wsTestChat) {
		t.Fatal("join test chat")
	}

	message := eventJSON(t, realtime.EventUserTyping, "", map[string]any{
		"chat_id": wsTestChat,
	})
	hub.handleInbound(client, message)
	hub.handleInbound(client, message)
	_ = nextEnvelope(t, client)
	_ = nextEnvelope(t, client)

	var typingStarts int
	for _, delivery := range publisher.snapshot() {
		if delivery.Envelope.Event != realtime.EventUserTyping {
			continue
		}
		var payload realtime.TypingPayload
		if err := json.Unmarshal(delivery.Envelope.Data, &payload); err != nil {
			t.Fatalf("decode typing event: %v", err)
		}
		if payload.Typing {
			typingStarts++
		}
	}
	if typingStarts != 1 {
		t.Fatalf("typing starts = %d, want 1", typingStarts)
	}
	if gateway.authorizeCalls != 1 {
		t.Fatalf("typing authorization calls = %d, want 1", gateway.authorizeCalls)
	}
}

func TestOversizedFrameRequestsMessageTooBigClose(t *testing.T) {
	hub := NewHubWithDependencies(nil, nil, nil, &fakePublisher{})
	client := newClient(nil, wsTestUser, "reader")

	code, _ := hub.handleInbound(client, []byte(strings.Repeat("x", maxInboundFrameSize+1)))
	if code != fiberws.CloseMessageTooBig {
		t.Fatalf("close code = %d, want %d", code, fiberws.CloseMessageTooBig)
	}
}

func TestBackpressureRequestsRetryClose(t *testing.T) {
	client := newClient(nil, wsTestUser, "reader")
	for i := 0; i < cap(client.Send); i++ {
		client.Send <- []byte("queued")
	}

	if client.enqueue([]byte("overflow")) {
		t.Fatal("enqueue succeeded despite full buffer")
	}
	select {
	case request := <-client.closeRequest:
		if request.code != fiberws.CloseTryAgainLater {
			t.Fatalf("close code = %d, want %d", request.code, fiberws.CloseTryAgainLater)
		}
	default:
		t.Fatal("backpressure did not request a graceful close")
	}
}

func TestDisconnectRemovesSubscriptionsBeforeReconnect(t *testing.T) {
	publisher := &fakePublisher{}
	hub := NewHubWithDependencies(nil, nil, &fakeChatGateway{}, publisher)
	first := newClient(nil, wsTestUser, "reader")
	if !hub.registerClient(first) {
		t.Fatal("register first client")
	}
	added, firstPresence, limited := hub.joinChat(first, "chat:"+wsTestChat)
	if !added || !firstPresence || limited {
		t.Fatalf("unexpected first join: added=%v presence=%v limited=%v", added, firstPresence, limited)
	}

	hub.unregisterClient(first)
	if hub.isSubscribed(first, "chat:"+wsTestChat) {
		t.Fatal("disconnected client retained chat subscription")
	}
	hub.mu.RLock()
	oldIndexed := len(hub.clientsByChan["chat:"+wsTestChat])
	hub.mu.RUnlock()
	if oldIndexed != 0 {
		t.Fatalf("old channel index has %d clients", oldIndexed)
	}

	second := newClient(nil, wsTestUser, "reader")
	if !hub.registerClient(second) {
		t.Fatal("register second client")
	}
	defer hub.unregisterClient(second)
	added, firstPresence, limited = hub.joinChat(second, "chat:"+wsTestChat)
	if !added || !firstPresence || limited {
		t.Fatalf("unexpected reconnect join: added=%v presence=%v limited=%v", added, firstPresence, limited)
	}
}

func TestPresenceDebounceSuppressesReconnectFlapping(t *testing.T) {
	publisher := &fakePublisher{}
	hub := NewHubWithDependencies(nil, nil, &fakeChatGateway{}, publisher)
	hub.presenceDebounce = 20 * time.Millisecond

	first := newClient(nil, wsTestUser, "reader")
	if !hub.registerClient(first) {
		t.Fatal("register first client")
	}
	if !hub.addSubscription(first, "chat:"+wsTestChat) {
		t.Fatal("join first client")
	}
	hub.updatePresence(wsTestUser, wsTestChat, true)
	hub.unregisterClient(first)

	second := newClient(nil, wsTestUser, "reader")
	if !hub.registerClient(second) {
		t.Fatal("register second client")
	}
	if !hub.addSubscription(second, "chat:"+wsTestChat) {
		t.Fatal("join second client")
	}
	hub.updatePresence(wsTestUser, wsTestChat, true)
	time.Sleep(50 * time.Millisecond)

	var onlineEvents int
	var offlineEvents int
	for _, delivery := range publisher.snapshot() {
		switch delivery.Envelope.Event {
		case realtime.EventUserOnline:
			onlineEvents++
		case realtime.EventUserOffline:
			offlineEvents++
		}
	}
	if onlineEvents != 1 || offlineEvents != 0 {
		t.Fatalf("presence events online=%d offline=%d, want 1/0", onlineEvents, offlineEvents)
	}

	hub.unregisterClient(second)
	hub.Close()
}

func eventJSON(t *testing.T, event string, operationKey string, payload any) []byte {
	t.Helper()
	data, err := json.Marshal(payload)
	if err != nil {
		t.Fatalf("marshal payload: %v", err)
	}
	envelope := realtime.Envelope{
		Version:      realtime.ProtocolVersion,
		Event:        event,
		RequestID:    "request-1",
		OperationKey: operationKey,
		Data:         data,
	}
	encoded, err := json.Marshal(envelope)
	if err != nil {
		t.Fatalf("marshal envelope: %v", err)
	}
	return encoded
}

func nextEnvelope(t *testing.T, client *Client) realtime.Envelope {
	t.Helper()
	select {
	case data := <-client.Send:
		var envelope realtime.Envelope
		if err := json.Unmarshal(data, &envelope); err != nil {
			t.Fatalf("decode envelope: %v", err)
		}
		return envelope
	case <-time.After(time.Second):
		t.Fatal("timed out waiting for realtime envelope")
		return realtime.Envelope{}
	}
}
