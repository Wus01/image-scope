package com.example.demo.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.demo.dto.PreviewResult;

import net.coobird.thumbnailator.Thumbnails;

@Service
public class ImageProcessorService {

    private static final int PREVIEW_MAX_WIDTH = 1280;
    private static final int PREVIEW_MAX_HEIGHT = 1280;
    private static final long MAX_WORKING_PIXELS = 12_000_000L;
    private static final double JPEG_QUALITY = 0.82;

    public PreviewResult createPreview(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {

            ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream);

            if (imageInputStream == null) {
                throw new IllegalArgumentException("이미지 입력 스트림을 생성할 수 없습니다.");
            }

            try (imageInputStream) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);

                if (!readers.hasNext()) {
                    throw new IllegalArgumentException("지원하지 않는 이미지 형식입니다.");
                }

                ImageReader reader = readers.next();

                try {
                    reader.setInput(imageInputStream, false, true);

                    int originalWidth = reader.getWidth(0);
                    int originalHeight = reader.getHeight(0);

                    String originalFormat = reader.getFormatName().toLowerCase(Locale.ROOT);

                    ImageReadParam readParam = reader.getDefaultReadParam();

                    int subsampling = calculateSubsampling(originalWidth, originalHeight);

                    if (subsampling > 1) {
                        readParam.setSourceSubsampling(
                                subsampling,
                                subsampling,
                                0,
                                0
                        );
                    }

                    // GIF는 첫 번째 프레임만 읽는다.
                    BufferedImage sourceImage = reader.read(0, readParam);

                    String outputFormat = determineOutputFormat(originalFormat, sourceImage);

                    int[] targetSize = calculateTargetSize(sourceImage.getWidth(), sourceImage.getHeight());

                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                    var builder = Thumbnails.of(sourceImage)
                            .size(targetSize[0], targetSize[1])
                            .outputFormat(outputFormat);

                    if ("jpg".equals(outputFormat)) {
                        builder.outputQuality(JPEG_QUALITY);
                    }

                    builder.toOutputStream(outputStream);

                    String mimeType = "png".equals(outputFormat) ? "image/png" : "image/jpeg";

                    return new PreviewResult(
                            outputStream.toByteArray(),
                            outputFormat,
                            mimeType,
                            targetSize[0],
                            targetSize[1]
                        );

                } finally {
                    reader.dispose();
                }
            }

        } catch (IOException exception) {
            throw new IllegalStateException("미리보기 이미지 생성에 실패했습니다.", exception);
        }
    }

    private int calculateSubsampling(int width, int height) {
        long sourcePixels = (long) width * height;

        if (sourcePixels <= MAX_WORKING_PIXELS) {
            return 1;
        }

        double requiredReduction = Math.sqrt((double) sourcePixels / MAX_WORKING_PIXELS);

        return Math.max(1, (int) Math.ceil(requiredReduction));
    }

    private int[] calculateTargetSize(int width, int height) {
        double scale = Math.min(
                1.0,
                Math.min(
                        (double) PREVIEW_MAX_WIDTH / width,
                        (double) PREVIEW_MAX_HEIGHT / height
                )
        );

        int targetWidth = Math.max(1, (int) Math.round(width * scale));

        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        return new int[] { targetWidth, targetHeight };
    }

    private String determineOutputFormat(String originalFormat, BufferedImage image) {
        if ("gif".equals(originalFormat)) {
            return "png";
        }

        if (image.getColorModel().hasAlpha()) {
            return "png";
        }

        return "jpg";
    }
}