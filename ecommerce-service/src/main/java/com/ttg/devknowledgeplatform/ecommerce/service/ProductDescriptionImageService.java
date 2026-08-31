package com.ttg.devknowledgeplatform.ecommerce.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Uploads an image to embed inline in a {@code Product.description} (via `gui`'s TipTap
 * editor) and returns a permanent URL for it — deliberately not part of {@link ProductService},
 * since this touches no {@code Product} entity at all (no {@code productId}, no
 * {@code ProductImage} row): the description is a plain HTML string, and an image referenced
 * inside it is conceptually an asset of the description's content, not a gallery photo.
 *
 * <p>Uses {@code infra}'s {@code StorageService.uploadPublicImage} rather than the presigned
 * {@code uploadImage}/{@code getPresignedUrl} pair {@code ProductServiceImpl}'s gallery-image
 * upload uses — a presigned URL baked once into stored HTML would silently expire and break,
 * since (unlike a gallery image, re-resolved fresh by {@code ProductMapper} on every read) nothing
 * ever re-processes {@code description} to mint a new one. See {@code StorageService}'s own
 * Javadoc for the full reasoning, including why the product gallery is deliberately *not* made
 * public the same way.
 */
public interface ProductDescriptionImageService {

    /**
     * Validates and uploads {@code file}, returning a permanent, never-expiring URL.
     *
     * @param file the multipart file from the HTTP request
     * @return the permanent URL
     * @throws com.ttg.devknowledgeplatform.common.exception.ApiException if the file isn't an image or exceeds the size limit
     */
    String upload(MultipartFile file);
}
