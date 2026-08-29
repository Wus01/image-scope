package com.example.demo.domain;

public record ImageVariant(
        String id,
        String imageId,
        String variantType,
        String format,
        long fileSize,
        int width,
        int height,
        String objectKey
) {
}