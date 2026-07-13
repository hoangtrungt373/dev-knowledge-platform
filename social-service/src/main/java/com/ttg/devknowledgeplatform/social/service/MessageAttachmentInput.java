package com.ttg.devknowledgeplatform.social.service;

/**
 * Optional attachment metadata for a DM or channel message, shared by {@link DmService} and
 * {@link GroupService}. {@code objectKey} is a MinIO object key already uploaded by the caller
 * (via {@code StorageService}), not a URL — resolved to a presigned URL by {@code api}'s mapper
 * at read time, same pattern as avatar images.
 */
public record MessageAttachmentInput(String objectKey, String mimeType, String fileName, Long fileSize) {
}
