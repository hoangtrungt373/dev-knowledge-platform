package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.ProductSearchResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductSearchView;
import com.ttg.devknowledgeplatform.infra.service.StorageService;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Maps {@code ProductSearchView} rows (the CQRS read model) to the public browse/search response
 * shape.
 *
 * <p>An abstract class rather than a plain interface — same reason as {@code ProductMapper}: it
 * needs an injected {@link StorageService} to resolve {@link ProductSearchView#getPrimaryImageStorageKey()}
 * into a time-limited presigned {@code primaryImageUrl}, the field the storefront grid actually
 * renders as a thumbnail. {@code primaryImageStorageKey} alone is a private MinIO object key —
 * useless to a browser with no credentials to fetch it directly.
 */
@Mapper(componentModel = "spring")
public abstract class ProductSearchViewMapper {

    @Autowired
    protected StorageService storageService;

    // product.id is available off the (possibly-uninitialized) @OneToOne proxy without a DB
    // round trip — Hibernate resolves an entity proxy's identifier from its own FK column.
    @Mapping(source = "product.id", target = "productId")
    @Mapping(target = "primaryImageUrl", ignore = true)
    public abstract ProductSearchResponse toResponse(ProductSearchView view);

    @AfterMapping
    protected void resolvePrimaryImageUrl(
            ProductSearchView view, @MappingTarget ProductSearchResponse.ProductSearchResponseBuilder builder) {
        String key = view.getPrimaryImageStorageKey();
        if (key != null) {
            builder.primaryImageUrl(storageService.getPresignedUrl(key));
        }
    }
}
