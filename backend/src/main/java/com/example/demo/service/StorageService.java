package com.example.demo.service;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import com.example.demo.dto.PreviewResult;

@Service
public class StorageService {

    private final S3Client s3Client;
    private final String bucket;

    public StorageService(S3Client s3Client,  @Value("${app.storage.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    public String uploadOriginal(String imageId, MultipartFile file, String mimeType) {
        String extension = determineExtension(mimeType);

        String objectKey = "originals/" + imageId + "/original." + extension;

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(mimeType)
                .contentLength(file.getSize())
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

            return objectKey;

        } catch (IOException | S3Exception exception) {
            throw new IllegalStateException(
                    "원본 이미지 Storage 저장에 실패했습니다.",
                    exception
            );
        }
    }

    public void deleteQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        try {
            DeleteObjectRequest request =
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .build();

            s3Client.deleteObject(request);

        } catch (S3Exception ignored) {
            // DB 저장 실패 시 보상 삭제용이므로 기존 오류를 유지한다.
        }
    }

    private String determineExtension(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 이미지 형식입니다."
            );
        };
    }

    public String uploadPreview(String imageId, PreviewResult preview) {
        String objectKey =
                "previews/" + imageId
                + "/preview." + preview.format();

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(preview.mimeType())
                .contentLength((long) preview.bytes().length)
                .build();

        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromBytes(preview.bytes())
            );

            return objectKey;

        } catch (S3Exception exception) {
            throw new IllegalStateException(
                    "미리보기 이미지 Storage 저장에 실패했습니다.",
                    exception
            );
        }
    }
}