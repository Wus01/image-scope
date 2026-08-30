package com.example.demo.dto;

import java.time.OffsetDateTime;

public record ImageListItemResponse(
        String imageId,
        String originalName,
        String mimeType,
        long originalSize,
        Long previewSize,
        Double reductionRatePercent,
        int width,
        int height,
        double megapixels,
        long estimatedMemoryBytes,
        String status,
        String message,
        String previewUrl,
        OffsetDateTime createdAt
) {
}
