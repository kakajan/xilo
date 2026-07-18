package minio

import "testing"

func TestGetURL_PublicHTTPS(t *testing.T) {
	d := &Driver{
		bucket:         "xilo-media",
		publicEndpoint: "brain.aile.ir",
		useSSL:         false,
		publicUseSSL:   true,
	}
	got := d.GetURL("user/id/avatar.png")
	want := "https://brain.aile.ir/xilo-media/user/id/avatar.png"
	if got != want {
		t.Fatalf("got %q want %q", got, want)
	}
}

func TestGetURL_InternalHTTP(t *testing.T) {
	d := &Driver{
		bucket:         "xilo-media",
		publicEndpoint: "127.0.0.1:9000",
		useSSL:         false,
		publicUseSSL:   false,
	}
	got := d.GetURL("k.png")
	want := "http://127.0.0.1:9000/xilo-media/k.png"
	if got != want {
		t.Fatalf("got %q want %q", got, want)
	}
}
