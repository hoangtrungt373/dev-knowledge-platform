package com.ttg.devknowledgeplatform.infra.service.impl;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.infra.config.storage.StorageProperties;
import com.ttg.devknowledgeplatform.infra.service.StorageService;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class StorageServiceImpl implements StorageService {

    /**
     * The only bucket prefix ever granted anonymous read access (see {@link #ensurePublicReadPolicy}) —
     * everything else in the bucket stays fully private, presigned-URL-only. {@link #uploadPublicImage}
     * always writes under this prefix regardless of the caller's own {@code keyPrefix}, so this is the
     * single source of truth both sides (the policy grant and the upload path) agree on.
     */
    static final String PUBLIC_IMAGE_PREFIX = "description-images/";

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
        ensurePublicReadPolicy();
    }

    /**
     * Grants anonymous {@code s3:GetObject} on {@link #PUBLIC_IMAGE_PREFIX} only — every other
     * object in the bucket (product galleries, avatars, etc.) is unaffected and stays reachable
     * only via {@link #getPresignedUrl}. Runs on every startup, not just first bucket creation
     * (setting the same policy document twice is a no-op) — a bucket that already existed before
     * this prefix was introduced would otherwise never pick up the grant.
     *
     * <p>{@code setBucketPolicy} replaces the bucket's entire policy document rather than adding to
     * it, but that's fine here — this is the only place in the reactor that ever sets one, so there
     * is nothing to clobber.
     */
    private void ensurePublicReadPolicy() {
        String policy = """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": {"AWS": ["*"]},
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/%s*"]
                    }
                  ]
                }
                """.formatted(props.getBucket(), PUBLIC_IMAGE_PREFIX);
        try {
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(props.getBucket()).config(policy).build());
        } catch (Exception e) {
            log.error("Failed to set public-read policy on '{}/{}': {}", props.getBucket(), PUBLIC_IMAGE_PREFIX, e.getMessage());
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
        Validator.isFalse(contentType == null || !contentType.startsWith("image/"),
                CommonErrorCode.VALIDATION_FIELD_INVALID, "Only image files are allowed");
        Validator.isFalse(file.getSize() > 5L * 1024 * 1024,
                CommonErrorCode.VALIDATION_FIELD_INVALID, "File must not exceed 5 MB");
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
    public String uploadPublicImage(String keyPrefix, MultipartFile file) {
        String objectKey = uploadImage(PUBLIC_IMAGE_PREFIX + keyPrefix, file);
        return props.getEndpoint() + "/" + props.getBucket() + "/" + objectKey;
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
