package service

import (
	"context"

	"github.com/xilo-platform/xilo/internal/auth/model"
)

type deviceMetadataContextKey struct{}

func ContextWithDeviceMetadata(ctx context.Context, meta *model.DeviceMetadata) context.Context {
	if meta == nil {
		return ctx
	}
	return context.WithValue(ctx, deviceMetadataContextKey{}, meta)
}

func DeviceMetadataFromContext(ctx context.Context) *model.DeviceMetadata {
	meta, _ := ctx.Value(deviceMetadataContextKey{}).(*model.DeviceMetadata)
	return meta
}
