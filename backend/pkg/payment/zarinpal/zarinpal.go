package zarinpal

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/xilo-platform/xilo/pkg/payment"
)

const (
	baseURL       = "https://payment.zarinpal.com/pg/v4/payment"
	sandboxURL    = "https://sandbox.zarinpal.com/pg/v4/payment"
	gatewayURL    = "https://payment.zarinpal.com/pg/StartPay"
	sandboxGateway = "https://sandbox.zarinpal.com/pg/StartPay"
)

var errorMessages = map[int]string{
	-9:  "Validation error: check merchant_id, callback_url, description, or amount",
	-10: "Terminal is not valid: check merchant_id or IP address",
	-11: "Terminal is not active: please contact Zarinpal support",
	-12: "Too many attempts: please try again later",
	-13: "Terminal limit reached: please complete verification documents",
	-14: "Callback URL domain does not match the registered terminal domain",
	-15: "Terminal is suspended: please contact Zarinpal support",
	-16: "Terminal user level is not valid (below silver level)",
	-17: "Terminal user level is not valid (blue level restriction)",
	-18: "Referrer address does not match the registered domain",
	-19: "Terminal user transactions are banned",
	-30: "Terminal does not allow floating wages",
	-31: "Please add default bank account in panel for wages",
	-32: "Total wages (floating) exceeds max amount",
	-33: "Wages percentage is not valid",
	-34: "Total wages (fixed) exceeds max amount",
	-35: "Number of wage recipients exceeds limit",
	-36: "Minimum wage amount is 10,000 Rials",
	-37: "One or more IBANs for wages are inactive",
	-38: "Wages need to set IBAN in Shaparak",
	-39: "Wages error: please contact Zarinpal support",
	-40: "Invalid extra params: expire_in is not valid",
	-41: "Maximum amount is 100,000,000 tomans",
	-50: "Session not valid: amounts do not match",
	-51: "Session not valid: payment was not successful",
	-52: "Unexpected error: please contact Zarinpal support",
	-53: "Session does not belong to this merchant_id",
	-54: "Invalid authority",
	-55: "Transaction not found",
	-60: "Session cannot be reversed with bank",
	-61: "Session is not in success status or already reversed",
	-62: "Terminal IP limit must be active",
	-63: "Maximum time for reverse (30 minutes) has expired",
}

type Driver struct {
	merchantID string
	sandbox    bool
	client     *http.Client
}

func NewDriver(merchantID string, sandbox bool) *Driver {
	return &Driver{
		merchantID: merchantID,
		sandbox:    sandbox,
		client: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

func (d *Driver) Name() string {
	return "zarinpal"
}

func (d *Driver) IsAvailable() bool {
	return d.merchantID != ""
}

func (d *Driver) apiURL(path string) string {
	if d.sandbox {
		return sandboxURL + path
	}
	return baseURL + path
}

func (d *Driver) gatewayBaseURL() string {
	if d.sandbox {
		return sandboxGateway
	}
	return gatewayURL
}

type requestPayload struct {
	MerchantID  string                 `json:"merchant_id"`
	Amount      int64                  `json:"amount"`
	Currency    string                 `json:"currency,omitempty"`
	Description string                 `json:"description"`
	CallbackURL string                 `json:"callback_url"`
	ReferrerID  string                 `json:"referrer_id,omitempty"`
	Metadata    *payment.PaymentMetadata `json:"metadata,omitempty"`
}

type requestResponse struct {
	Data   requestResponseData `json:"data"`
	Errors []string            `json:"errors"`
}

type requestResponseData struct {
	Code      int    `json:"code"`
	Message   string `json:"message"`
	Authority string `json:"authority"`
	FeeType   string `json:"fee_type"`
	Fee       int64  `json:"fee"`
}

type verifyPayload struct {
	MerchantID string `json:"merchant_id"`
	Amount     int64  `json:"amount"`
	Authority  string `json:"authority"`
}

type verifyResponse struct {
	Data   verifyResponseData `json:"data"`
	Errors []string           `json:"errors"`
}

type verifyResponseData struct {
	Code     int    `json:"code"`
	Message  string `json:"message"`
	RefID    int64  `json:"ref_id"`
	CardPAN  string `json:"card_pan"`
	CardHash string `json:"card_hash"`
	FeeType  string `json:"fee_type"`
	Fee      int64  `json:"fee"`
}

func (d *Driver) RequestPayment(ctx context.Context, req *payment.PaymentRequest) (*payment.PaymentResponse, error) {
	payload := requestPayload{
		MerchantID:  d.merchantID,
		Amount:      req.Amount,
		Currency:    req.Currency,
		Description: req.Description,
		CallbackURL: req.CallbackURL,
		ReferrerID:  req.ReferrerID,
		Metadata:    req.Metadata,
	}

	body, err := json.Marshal(payload)
	if err != nil {
		return nil, fmt.Errorf("zarinpal: marshal request: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, d.apiURL("/request.json"), bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("zarinpal: create request: %w", err)
	}
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")

	resp, err := d.client.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("zarinpal: request payment: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("zarinpal: read response: %w", err)
	}

	var zResp requestResponse
	if err := json.Unmarshal(respBody, &zResp); err != nil {
		return nil, fmt.Errorf("zarinpal: unmarshal response: %w", err)
	}

	if zResp.Data.Code != 100 {
		return nil, d.errorFromCode(zResp.Data.Code)
	}

	return &payment.PaymentResponse{
		Code:       zResp.Data.Code,
		Message:    zResp.Data.Message,
		Authority:  zResp.Data.Authority,
		FeeType:    zResp.Data.FeeType,
		Fee:        zResp.Data.Fee,
		GatewayURL: d.gatewayBaseURL() + "/" + zResp.Data.Authority,
	}, nil
}

func (d *Driver) VerifyPayment(ctx context.Context, req *payment.VerifyRequest) (*payment.VerifyResponse, error) {
	payload := verifyPayload{
		MerchantID: d.merchantID,
		Amount:     req.Amount,
		Authority:  req.Authority,
	}

	body, err := json.Marshal(payload)
	if err != nil {
		return nil, fmt.Errorf("zarinpal: marshal verify: %w", err)
	}

	httpReq, err := http.NewRequestWithContext(ctx, http.MethodPost, d.apiURL("/verify.json"), bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("zarinpal: create verify request: %w", err)
	}
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("Accept", "application/json")

	resp, err := d.client.Do(httpReq)
	if err != nil {
		return nil, fmt.Errorf("zarinpal: verify payment: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("zarinpal: read verify response: %w", err)
	}

	var zResp verifyResponse
	if err := json.Unmarshal(respBody, &zResp); err != nil {
		return nil, fmt.Errorf("zarinpal: unmarshal verify response: %w", err)
	}

	if zResp.Data.Code != 100 && zResp.Data.Code != 101 {
		return nil, d.errorFromCode(zResp.Data.Code)
	}

	return &payment.VerifyResponse{
		Code:     zResp.Data.Code,
		Message:  zResp.Data.Message,
		RefID:    zResp.Data.RefID,
		CardPAN:  zResp.Data.CardPAN,
		CardHash: zResp.Data.CardHash,
		FeeType:  zResp.Data.FeeType,
		Fee:      zResp.Data.Fee,
	}, nil
}

func (d *Driver) errorFromCode(code int) error {
	msg, ok := errorMessages[code]
	if !ok {
		msg = fmt.Sprintf("Unknown error (code: %d)", code)
	}
	return &PaymentError{Code: code, Message: msg}
}

type PaymentError struct {
	Code    int
	Message string
}

func (e *PaymentError) Error() string {
	return fmt.Sprintf("zarinpal error %d: %s", e.Code, e.Message)
}

func IsErrorCode(err error, code int) bool {
	pe, ok := err.(*PaymentError)
	if !ok {
		return false
	}
	return pe.Code == code
}
