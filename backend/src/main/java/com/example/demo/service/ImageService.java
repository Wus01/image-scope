package com.example.demo.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.demo.domain.ImageJob;
import com.example.demo.domain.ImageVariant;
import com.example.demo.dto.ImageListItemResponse;
import com.example.demo.dto.ImageUploadResponse;
import com.example.demo.dto.PreviewResult;
import com.example.demo.dto.StoredImage;
import com.example.demo.mapper.ImageMapper;
import java.util.Locale;

@Service
public class ImageService {
    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 30_000;
    private static final long MAX_PIXEL_COUNT = 300_000_000L;
    private final ImageMapper imageMapper;

    private final StorageService storageService;
    private final ImageProcessorService imageProcessorService;

    public ImageService(
            ImageMapper imageMapper,
            StorageService storageService,
            ImageProcessorService imageProcessorService
    ) {
        this.imageMapper = imageMapper;
        this.storageService = storageService;
        this.imageProcessorService = imageProcessorService;
    }

    @Transactional
    public ImageUploadResponse analyzeAndSave(MultipartFile file, String clientId){
        validateClientId(clientId);
        validateFile(file);

        ImageMetadata metadata = readMetadata(file);

        long pixelCount = (long) metadata.width() * metadata.height();
        long estimatedMemoryBytes = pixelCount > Long.MAX_VALUE / 4 ? Long.MAX_VALUE : pixelCount * 4;

        double megapixels = Math.round(pixelCount / 1_000_000.0 * 100.0) / 100.0;

        boolean rejected = metadata.width() > MAX_IMAGE_DIMENSION
                            || metadata.height() > MAX_IMAGE_DIMENSION
                            || pixelCount > MAX_PIXEL_COUNT;

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename());

        String imageId = UUID.randomUUID().toString();

        if (rejected) {
            ImageJob rejectedJob = new ImageJob(
                    imageId,
                    clientId,
                    originalName,
                    metadata.mimeType(),
                    file.getSize(),
                    metadata.width(),
                    metadata.height(),
                    megapixels,
                    estimatedMemoryBytes,
                    "REJECTED",
                    "이미지 최대 처리 기준(한 변 30,000px, 300MP)을 초과했습니다.",
                    null
            );

            insertImageJob(rejectedJob);

            return ImageUploadResponse.from(rejectedJob, null);
        }
        String originalObjectKey = null;
        String previewObjectKey = null;

        try {
            originalObjectKey = storageService.uploadOriginal(imageId, file, metadata.mimeType());

            PreviewResult previewResult = null;
            String status = "COMPLETED";
            String failureReason = null;

            try {
                previewResult = imageProcessorService.createPreview(file);
                previewObjectKey = storageService.uploadPreview(imageId, previewResult);                        

            } catch (RuntimeException previewException) {
                status = "FAILED";
                failureReason ="원본은 저장됐지만 미리보기 생성에 실패했습니다.";
            }
            

            ImageJob imageJob = new ImageJob(
                    imageId,
                    clientId,
                    originalName,
                    metadata.mimeType(),
                    file.getSize(),
                    metadata.width(),
                    metadata.height(),
                    megapixels,
                    estimatedMemoryBytes,
                    status,
                    failureReason,
                    originalObjectKey
            );

            insertImageJob(imageJob);

            if (previewResult != null && previewObjectKey != null) {

                ImageVariant imageVariant = new ImageVariant(
                                                UUID.randomUUID().toString(),
                                                imageId,
                                                "PREVIEW",
                                                previewResult.format(),
                                                previewResult.bytes().length,
                                                previewResult.width(),
                                                previewResult.height(),
                                                previewObjectKey
                                            );
                        

                int insertedVariantCount = imageMapper.insertImageVariant(imageVariant);

                if (insertedVariantCount != 1) {
                    throw new IllegalStateException(
                            "미리보기 이미지 정보 저장에 실패했습니다."
                    );
                }
            }

            Long previewSize = previewResult == null ? null : (long) previewResult.bytes().length;

            return ImageUploadResponse.from(imageJob, previewSize);

        } catch (RuntimeException exception) {
            storageService.deleteQuietly(previewObjectKey);
            storageService.deleteQuietly(originalObjectKey);

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<ImageListItemResponse> getImages(String clientId) {
        validateClientId(clientId);

        return imageMapper.selectImagesByClientId(clientId);
    }

    @Transactional(readOnly = true)
    public StoredImage getPreview(String imageId, String clientId) {
        validateClientId(clientId);
        validateImageId(imageId);

        ImageVariant preview = imageMapper.selectPreviewByImageIdAndClientId(
                imageId,
                clientId
        );

        if (preview == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "미리보기 이미지를 찾을 수 없습니다."
            );
        }

        byte[] bytes = storageService.download(preview.objectKey());

        return new StoredImage(
                bytes,
                previewMimeType(preview.format())
        );
    }

    @Transactional(readOnly = true)
    public StoredImage getOriginal(String imageId, String clientId) {
        validateClientId(clientId);
        validateImageId(imageId);

        ImageJob imageJob = imageMapper.selectImageJobByIdAndClientId(imageId, clientId);

        if (imageJob == null || imageJob.originalObjectKey() == null || imageJob.originalObjectKey().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "원본 이미지를 찾을 수 없습니다."
            );
        }

        byte[] bytes = storageService.download(imageJob.originalObjectKey());

        return new StoredImage(bytes, imageJob.mimeType());
    }

    @Transactional
    public void deleteImage(String imageId, String clientId) {
        validateClientId(clientId);
        validateImageId(imageId);

        ImageJob imageJob = imageMapper.selectImageJobByIdAndClientId(imageId, clientId);

        if (imageJob == null) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "삭제할 이미지를 찾을 수 없습니다."
            );
        }

        List<String> variantObjectKeys = imageMapper.selectVariantObjectKeysByImageId(imageId);

        int deletedCount = imageMapper.deleteImageJobByIdAndClientId(imageId, clientId);

        if (deletedCount != 1) {
            throw new IllegalStateException("이미지 정보 삭제에 실패했습니다.");
        }

        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    variantObjectKeys.forEach(storageService::deleteQuietly);
                    storageService.deleteQuietly(imageJob.originalObjectKey());
                }
            }
        );
    }

    private void validateClientId(String clientId){
        try{
            UUID.fromString(clientId);

        } catch (IllegalArgumentException e){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Client-Id는 UUID 형식이어야 합니다");
        }
    }

    private void validateImageId(String imageId) {
        try {
            UUID.fromString(imageId);

        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "imageId는 UUID 형식이어야 합니다."
            );
        }
    }

    private String previewMimeType(String format) {
        return switch (format.toLowerCase()) {
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "gif" -> MediaType.IMAGE_GIF_VALUE;
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    private void validateFile(MultipartFile file){
        if(file == null || file.isEmpty()){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일을 선택해주세요");

        }
        if(file.getSize() > MAX_FILE_SIZE_BYTES){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 크키는 20MB를 초과할 수 없습니다.");

        }
    }

    private void insertImageJob(ImageJob imageJob) {
        int insertedCount =
                imageMapper.insertImageJob(imageJob);

        if (insertedCount != 1) {
            throw new IllegalStateException(
                    "이미지 처리 결과 저장에 실패했습니다."
            );
        }
    }

    private ImageMetadata readMetadata(MultipartFile file){
        try(InputStream inputStream = file.getInputStream()){
            
            ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream);

            if(imageInputStream == null){
                throw unsupportedImageException();

            }

            try(imageInputStream){
                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);

                if(!readers.hasNext()){
                    throw unsupportedImageException();

                }

                ImageReader reader = readers.next();
                
                try{
                    reader.setInput(imageInputStream, true, true);

                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);

                    String format = reader.getFormatName();

                    String mimeType = convertMimeType(format);

                    return new ImageMetadata(width, height, mimeType);
                } finally {
                    reader.dispose();
                }
            }
        } catch  (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "이미지 정보를 읽을 수 없습니다.",
                    exception
            );
        }
    }

    private String convertMimeType(String format) {
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            default -> throw unsupportedImageException();
        };
    }

    private ResponseStatusException unsupportedImageException() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "JPG, PNG, GIF 이미지만 업로드할 수 있습니다."
        );
    }

    private record ImageMetadata(
            int width,
            int height,
            String mimeType
    ) {
    }
}
