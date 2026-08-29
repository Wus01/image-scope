package com.example.demo.dto;

public record PreviewResult(
        byte[] bytes,
        String format,
        String mimeType,
        int width,
        int height
) {
}