package com.ttg.devknowledgeplatform.social.dto.messaging;

import jakarta.validation.constraints.NotBlank;

/**
 * Attachment metadata submitted alongside a message. {@code objectKey} must already exist in
 * MinIO — uploading the file itself is a separate, not-yet-built endpoint; this only references
 * an object already there.
 *
 * @param objectKey MinIO object key
 * @param mimeType  attachment content type
 * @param fileName  original file name
 * @param fileSize  size in bytes
 */
public record MessageAttachmentRequest(
        @NotBlank(message = "objectKey is required")
        String objectKey,
        String mimeType,
        String fileName,
        Long fileSize
) {
}
