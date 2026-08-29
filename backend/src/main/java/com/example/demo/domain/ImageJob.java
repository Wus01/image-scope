package com.example.demo.domain;

public record ImageJob(
        String id,
        String clientId,
        String originalName,
        String mimeType,
        long originalSize,
        int originalWidth,
        int originalHeight,
        double megapixels,
        long estimatedMemoryBytes,
        String status,
        String failureReason,
        String originalObjectKey
) {
}