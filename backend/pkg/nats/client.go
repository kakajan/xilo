package nats

import (
	"github.com/nats-io/nats.go"
)

type Client struct {
	Conn *nats.Conn
	JS   nats.JetStreamContext
}

func NewClient(url string) (*Client, error) {
	nc, err := nats.Connect(url)
	if err != nil {
		return nil, err
	}

	js, err := nc.JetStream()
	if err != nil {
		return nil, err
	}

	return &Client{Conn: nc, JS: js}, nil
}

func (c *Client) Publish(subject string, data []byte) error {
	return c.Conn.Publish(subject, data)
}

func (c *Client) Subscribe(subject string, handler func([]byte)) (*nats.Subscription, error) {
	return c.Conn.Subscribe(subject, func(m *nats.Msg) {
		handler(m.Data)
	})
}

func (c *Client) Close() {
	c.Conn.Close()
}
