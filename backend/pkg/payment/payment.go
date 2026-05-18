package payment

import "context"

type Driver interface {
	RequestPayment(ctx context.Context, req *PaymentRequest) (*PaymentResponse, error)
	VerifyPayment(ctx context.Context, req *VerifyRequest) (*VerifyResponse, error)
	Name() string
	IsAvailable() bool
}

type PaymentRequest struct {
	Amount      int64
	Currency    string
	Description string
	Metadata    *PaymentMetadata
	CallbackURL string
	ReferrerID  string
}

type PaymentMetadata struct {
	Mobile  string `json:"mobile,omitempty"`
	Email   string `json:"email,omitempty"`
	OrderID string `json:"order_id,omitempty"`
}

type PaymentResponse struct {
	Code      int    `json:"code"`
	Message   string `json:"message"`
	Authority string `json:"authority"`
	FeeType   string `json:"fee_type"`
	Fee       int64  `json:"fee"`
	GatewayURL string `json:"gateway_url"`
}

type VerifyRequest struct {
	Amount    int64  `json:"amount"`
	Authority string `json:"authority"`
}

type VerifyResponse struct {
	Code     int    `json:"code"`
	Message  string `json:"message"`
	RefID    int64  `json:"ref_id"`
	CardPAN  string `json:"card_pan"`
	CardHash string `json:"card_hash"`
	FeeType  string `json:"fee_type"`
	Fee      int64  `json:"fee"`
}

func IsSuccess(code int) bool {
	return code == 100 || code == 101
}
