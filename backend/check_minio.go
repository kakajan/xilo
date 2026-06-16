package main

import (
	"context"
	"fmt"
	"log"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

func main() {
	endpoint := "127.0.0.1:9010"
	accessKey := "minioadmin"
	secretKey := "minioadmin"
	// bucket := "xilo-media"

	client, err := minio.New(endpoint, &minio.Options{
		Creds:        credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure:       false,
		BucketLookup: minio.BucketLookupPath,
	})
	if err != nil {
		log.Fatalf("minio client init failed: %v", err)
	}

	ctx := context.Background()
	exists, err := client.BucketExists(ctx, "xilo-media")
	if err != nil {
		log.Fatalf("BucketExists failed: %v", err)
	}
	fmt.Printf("xilo-media exists: %t\n", exists)

	if !exists {
		err = client.MakeBucket(ctx, "xilo-media", minio.MakeBucketOptions{})
		if err != nil {
			log.Fatalf("MakeBucket failed: %v", err)
		}
		fmt.Println("MakeBucket succeeded")
	}

	buckets, err := client.ListBuckets(ctx)
	if err != nil {
		log.Fatalf("ListBuckets failed: %v", err)
	}

	fmt.Println("Available Buckets:")
	for _, b := range buckets {
		fmt.Printf("- %s\n", b.Name)
	}
}
