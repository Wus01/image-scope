package com.example.demo.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.example.demo.domain.ImageJob;
import com.example.demo.domain.ImageVariant;

@Mapper
public interface ImageMapper {

    int insertImageJob(ImageJob imageJob);
    int insertImageVariant(ImageVariant imageVariant);
}