package com.ttg.devknowledgeplatform.dto.messaging;

/**
 * Attachment metadata for a DM or channel message, present only when the message carries one.
 *
 * @param url      presigned download URL, resolved from the stored MinIO object key at read time
 * @param mimeType attachment content type
 * @param fileName original file name
 * @param fileSize size in bytes
 */
public record MessageAttachmentResponse(String url, String mimeType, String fileName, Long fileSize) {
}
