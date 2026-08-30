package com.example.demo.controller;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.dto.ImageUploadResponse;
import com.example.demo.dto.ImageListItemResponse;
import com.example.demo.dto.StoredImage;
import com.example.demo.service.ImageService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/images")
public class ImageController {
    private final ImageService imageService;


    public ImageController(ImageService imageService){
        this.imageService = imageService;
    }
    
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageUploadResponse> upload(@RequestHeader("X-Client-Id") String clientId, @RequestPart("file") MultipartFile file){
        ImageUploadResponse response = imageService.analyzeAndSave(file, clientId);

        HttpStatus status = "REJECTED".equals(response.status())
                            ? HttpStatus.UNPROCESSABLE_ENTITY
                            : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);

    }

    @GetMapping
    public List<ImageListItemResponse> getImages(
            @RequestHeader("X-Client-Id") String clientId
    ) {
        return imageService.getImages(clientId);
    }

    @GetMapping("/{imageId}/preview")
    public ResponseEntity<byte[]> getPreview(
            @PathVariable String imageId,
            @RequestHeader("X-Client-Id") String clientId
    ) {
        StoredImage preview = imageService.getPreview(imageId, clientId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(preview.mimeType()))
                .contentLength(preview.bytes().length)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(preview.bytes());
    }
    
    
    
}
