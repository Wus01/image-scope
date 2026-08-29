package com.example.demo.dto;

import com.example.demo.domain.ImageJob;

public record ImageUploadResponse(
        String imageId,
        String clientId,
        String originalName,
        String mimeType,
        long originalSize,
        int width,
        int height,
        double megapixels,
        long estimatedMemoryBytes,
        String status,
        String message
) {

    public static ImageUploadResponse from(ImageJob job) {
        String message = switch (job.status()) {
                case "COMPLETED" -> "원본 및 미리보기 이미지 처리가 완료되었습니다.";

                case "FAILED" -> job.failureReason();

                case "REJECTED" -> job.failureReason();

                default -> "이미지 처리 중입니다.";
        };

        return new ImageUploadResponse(
                job.id(),
                job.clientId(),
                job.originalName(),
                job.mimeType(),
                job.originalSize(),
                job.originalWidth(),
                job.originalHeight(),
                job.megapixels(),
                job.estimatedMemoryBytes(),
                job.status(),
                message
        );
    }
}