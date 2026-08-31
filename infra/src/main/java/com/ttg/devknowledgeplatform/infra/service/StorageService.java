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
     * Validates, uploads an image under a bucket prefix that's world-readable with no signature
     * needed (unlike every other upload in this interface), and returns a permanent, never-expiring
     * URL — for content that gets embedded once into a stored document and never re-resolved on
     * read (e.g. an image inside a rich-text field's saved HTML), where a presigned URL from
     * {@link #getPresignedUrl} would silently go stale the moment it expired. Same validation as
     * {@link #uploadImage} (image-only content type, 5 MB limit); the actual object key always
     * lands under this implementation's own public-prefix folder regardless of {@code keyPrefix},
     * so a caller can't accidentally publish something outside it.
     *
     * <p>Not appropriate for anything that must stay access-controlled (e.g. a product's own
     * gallery images, which can belong to a not-yet-published/deactivated product) — see the
     * discussion in {@code ecommerce-service}'s product-description-image work for why those two
     * cases are treated differently despite both being "an image for a product."
     *
     * @param keyPrefix path within the public prefix, without extension (e.g. "uuid")
     * @param file      the multipart file from the HTTP request
     * @return a permanent, unsigned URL that never expires
     */
    String uploadPublicImage(String keyPrefix, MultipartFile file);

    /**
     * Delete an object from the configured bucket. No-ops silently if the key does not exist.
     */
    void delete(String objectKey);
}
