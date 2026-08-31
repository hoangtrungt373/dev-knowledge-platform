package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.ProductImageResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductVariantResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductImage;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.infra.service.StorageService;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Collectors;

/**
 * Maps {@code Product}/{@code ProductVariant}/{@code ProductImage} entities to their REST
 * response shapes.
 *
 * <p>An abstract class rather than a plain interface — {@link #resolveImageUrl} needs an injected
 * {@link StorageService} to resolve {@code ProductImage.storageKey} into a time-limited presigned
 * URL the admin GUI can actually render, and MapStruct interfaces can't hold instance fields (same
 * pattern as {@code identity-service}'s {@code UserMapper}, which resolves an avatar URL the same
 * way). {@link #resolveTagIds} doesn't strictly need to live here for the same reason (no injected
 * dependency involved), but sits alongside it for the same "derived from a nav-only collection,
 * not a direct field" shape.
 */
@Mapper(componentModel = "spring")
public abstract class ProductMapper {

    @Autowired
    protected StorageService storageService;

    @Mapping(source = "productCategory.id", target = "productCategoryId")
    @Mapping(source = "productCategory.name", target = "categoryName")
    @Mapping(source = "dteCreation", target = "createdAt")
    @Mapping(source = "dteLastModification", target = "updatedAt")
    @Mapping(target = "tagIds", ignore = true)
    public abstract ProductResponse toResponse(Product product);

    public abstract ProductVariantResponse toVariantResponse(ProductVariant variant);

    @Mapping(target = "url", ignore = true)
    public abstract ProductImageResponse toImageResponse(ProductImage image);

    @AfterMapping
    protected void resolveImageUrl(
            ProductImage image, @MappingTarget ProductImageResponse.ProductImageResponseBuilder builder) {
        builder.url(storageService.getPresignedUrl(image.getStorageKey()));
    }

    @AfterMapping
    protected void resolveTagIds(Product product, @MappingTarget ProductResponse.ProductResponseBuilder builder) {
        builder.tagIds(product.getProductTagAssignments().stream()
                .map(assignment -> assignment.getProductTag().getId())
                .collect(Collectors.toSet()));
    }
}
