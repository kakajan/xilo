package service

import (
	"bytes"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"testing"
)

func TestProcessAvatar_JPEG(t *testing.T) {
	src := image.NewRGBA(image.Rect(0, 0, 400, 200))
	for y := 0; y < 200; y++ {
		for x := 0; x < 400; x++ {
			src.Set(x, y, color.RGBA{R: 200, G: 80, B: 40, A: 255})
		}
	}
	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, src, &jpeg.Options{Quality: 90}); err != nil {
		t.Fatalf("encode jpeg: %v", err)
	}

	out, err := processAvatar(buf.Bytes())
	if err != nil {
		t.Fatalf("processAvatar: %v", err)
	}

	decoded, err := png.Decode(bytes.NewReader(out))
	if err != nil {
		t.Fatalf("decode result: %v", err)
	}
	if decoded.Bounds().Dx() != 256 || decoded.Bounds().Dy() != 256 {
		t.Fatalf("expected 256x256, got %dx%d", decoded.Bounds().Dx(), decoded.Bounds().Dy())
	}
}

func TestNormalizeMimeType_AudioExtensions(t *testing.T) {
	cases := map[string]string{
		"track.mp3":  "audio/mpeg",
		"clip.m4a":   "audio/mp4",
		"voice.aac":  "audio/aac",
		"sound.ogg":  "audio/ogg",
		"wave.wav":   "audio/wav",
		"note.webm":  "audio/webm",
	}
	for name, want := range cases {
		got := normalizeMimeType("application/octet-stream", name)
		if got != want {
			t.Fatalf("%s: got %q want %q", name, got, want)
		}
	}
}

func TestIsAllowedUploadMime_AudioAndImage(t *testing.T) {
	if !isAllowedUploadMime("audio/mpeg") {
		t.Fatal("expected audio/mpeg allowed")
	}
	if !isAllowedUploadMime("image/png") {
		t.Fatal("expected image/png allowed")
	}
	if isAllowedUploadMime("video/mp4") {
		t.Fatal("expected video/mp4 rejected")
	}
	if isAllowedUploadMime("application/pdf") {
		t.Fatal("expected application/pdf rejected")
	}
}

func TestMaxUploadSize_ByKind(t *testing.T) {
	if maxUploadSize("audio/mpeg") != maxAudioFileSize {
		t.Fatalf("audio max = %d, want %d", maxUploadSize("audio/mpeg"), maxAudioFileSize)
	}
	if maxUploadSize("image/jpeg") != maxImageFileSize {
		t.Fatalf("image max = %d, want %d", maxUploadSize("image/jpeg"), maxImageFileSize)
	}
}

func TestProcessAvatar_PNG(t *testing.T) {
	src := image.NewRGBA(image.Rect(0, 0, 100, 300))
	var buf bytes.Buffer
	if err := png.Encode(&buf, src); err != nil {
		t.Fatalf("encode png: %v", err)
	}

	out, err := processAvatar(buf.Bytes())
	if err != nil {
		t.Fatalf("processAvatar: %v", err)
	}
	if len(out) == 0 {
		t.Fatal("expected non-empty avatar bytes")
	}
}
