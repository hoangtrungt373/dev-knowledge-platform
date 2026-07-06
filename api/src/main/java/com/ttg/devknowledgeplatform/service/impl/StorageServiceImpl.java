package com.ttg.devknowledgeplatform.service.impl;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.config.storage.StorageProperties;
import com.ttg.devknowledgeplatform.service.StorageService;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class StorageServiceImpl implements StorageService {

    private final MinioClient minioClient;
    private final StorageProperties props;

    @PostConstruct
    public void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(props.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(props.getBucket()).build());
                log.info("Created MinIO bucket: {}", props.getBucket());
            }
        } catch (Exception e) {
            log.error("Failed to ensure MinIO bucket '{}' exists: {}", props.getBucket(), e.getMessage());
        }
    }

    @Override
    public void upload(String objectKey, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(props.getBucket())
                            .object(objectKey)
                            .stream(in, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
            log.debug("Uploaded object '{}' to bucket '{}'", objectKey, props.getBucket());
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to storage: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadImage(String keyPrefix, MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new ApiException(CommonErrorCode.VALIDATION_FIELD_INVALID, "Only image files are allowed");
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            throw new ApiException(CommonErrorCode.VALIDATION_FIELD_INVALID, "File must not exceed 5 MB");
        }
        String objectKey = keyPrefix + "." + extensionFor(contentType);
        upload(objectKey, file);
        return objectKey;
    }

    @Override
    public String getPresignedUrl(String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(props.getBucket())
                            .object(objectKey)
                            .method(Method.GET)
                            .expiry(props.getPresignedUrlExpiryMinutes(), TimeUnit.MINUTES)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate presigned URL: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(props.getBucket())
                            .object(objectKey)
                            .build());
            log.debug("Deleted object '{}' from bucket '{}'", objectKey, props.getBucket());
        } catch (Exception e) {
            log.warn("Failed to delete object '{}': {}", objectKey, e.getMessage());
        }
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
}
