package com.example.demo.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.example.demo.domain.ImageJob;
import com.example.demo.domain.ImageVariant;
import com.example.demo.dto.ImageListItemResponse;

@Mapper
public interface ImageMapper {

    int insertImageJob(ImageJob imageJob);
    int insertImageVariant(ImageVariant imageVariant);

    List<ImageListItemResponse> selectImagesByClientId(
            @Param("clientId") String clientId
    );

    ImageVariant selectPreviewByImageIdAndClientId(
            @Param("imageId") String imageId,
            @Param("clientId") String clientId
    );
}
