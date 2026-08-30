package com.example.demo.dto;

public record StoredImage(
        byte[] bytes,
        String mimeType
) {
}
