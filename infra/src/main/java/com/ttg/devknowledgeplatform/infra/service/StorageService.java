package com.ttg.devknowledgeplatform.infra.service;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    /**
     * Upload a raw file to the configured bucket.
     *
     * @param objectKey destination path within the bucket (e.g. "avatars/uuid.jpg")
     * @param file      the multipart file from the HTTP request
     */
    void upload(String objectKey, MultipartFile file);

    /**
     * Validate, upload an image, and return the full object key (prefix + resolved extension).
     * Enforces image-only content type and a 5 MB size limit.
     *
     * @param keyPrefix path within the bucket without extension (e.g. "avatars/uuid")
     * @param file      the multipart file from the HTTP request
     * @return the stored object key (e.g. "avatars/uuid.png")
     */
    String uploadImage(String keyPrefix, MultipartFile file);

    /**
     * Returns a time-limited presigned GET URL for a stored object.
     */
    String getPresignedUrl(String objectKey);

    /**
     * Delete an object from the configured bucket. No-ops silently if the key does not exist.
     */
    void delete(String objectKey);
}
