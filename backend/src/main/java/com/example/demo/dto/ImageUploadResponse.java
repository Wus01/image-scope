package com.example.demo.dto;

import com.example.demo.domain.ImageJob;

public record ImageUploadResponse(
        String imageId,
        String clientId,
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
        String message
) {

    private static final long HIGH_RESOLUTION_MEMORY_BYTES = 400L * 1024 * 1024;

    public static ImageUploadResponse from(ImageJob job, Long previewSize) {
        String message = switch (job.status()) {
                case "COMPLETED" ->
                        job.estimatedMemoryBytes()
                                > HIGH_RESOLUTION_MEMORY_BYTES
                                ? "고해상도 원본은 그대로 저장하고, 미리보기는 저메모리 샘플링으로 생성했습니다."
                                : "원본 및 미리보기 이미지 처리가 완료되었습니다.";

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
                previewSize,
                calculateReductionRatePercent(job.originalSize(), previewSize),
                job.originalWidth(),
                job.originalHeight(),
                job.megapixels(),
                job.estimatedMemoryBytes(),
                job.status(),
                message
        );
    }

    private static Double calculateReductionRatePercent(long originalSize, Long previewSize) {
        if (previewSize == null || originalSize == 0) {
            return null;
        }

        double reductionRate = (1.0 - (double) previewSize / originalSize) * 100.0;

        return Math.round(reductionRate * 100.0) / 100.0;
    }
}
