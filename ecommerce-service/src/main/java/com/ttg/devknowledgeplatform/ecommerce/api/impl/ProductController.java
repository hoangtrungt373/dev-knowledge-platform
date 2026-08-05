package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.api.ProductApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.CreateProductRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductImageRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductImageResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductVariantRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductVariantResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateProductImageSortOrderRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateProductRequest;
import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.mapper.ProductMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCommands;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Implementation of {@link ProductApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ProductController implements ProductApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "dteCreation");

    private final ProductService productService;
    private final ProductMapper productMapper;

    @Override
    public ResponseEntity<ProductResponse> create(CreateProductRequest request) {
        ProductCommands.Create command = new ProductCommands.Create(
                request.getName(), request.getDescription(), request.getProductCategoryId(),
                toVariantInputs(request.getVariants()), toImageInputs(request.getImages()));
        Product created = productService.create(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(productMapper.toResponse(created));
    }

    @Override
    public ResponseEntity<ProductResponse> update(Integer id, UpdateProductRequest request) {
        ProductCommands.Update command = new ProductCommands.Update(
                request.getName(), request.getDescription(), request.getProductCategoryId());
        Product updated = productService.update(id, command);
        return ResponseEntity.ok(productMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<ProductResponse> deactivate(Integer id) {
        return ResponseEntity.ok(productMapper.toResponse(productService.deactivate(id)));
    }

    @Override
    public ResponseEntity<ProductVariantResponse> addVariant(Integer id, ProductVariantRequest request) {
        ProductCommands.VariantInput input = new ProductCommands.VariantInput(
                request.getSku(), request.getPrice(), request.getStockQuantity(), request.getAttributes());
        var added = productService.addVariant(id, input);
        return ResponseEntity.status(HttpStatus.CREATED).body(productMapper.toVariantResponse(added));
    }

    @Override
    public ResponseEntity<Void> removeVariant(Integer id, Integer variantId) {
        productService.removeVariant(id, variantId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ProductImageResponse> addImage(Integer id, ProductImageRequest request) {
        ProductCommands.ImageInput input = new ProductCommands.ImageInput(request.getStorageKey(), request.getSortOrder());
        var added = productService.addImage(id, input);
        return ResponseEntity.status(HttpStatus.CREATED).body(productMapper.toImageResponse(added));
    }

    @Override
    public ResponseEntity<Void> removeImage(Integer id, Integer imageId) {
        productService.removeImage(id, imageId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ProductImageResponse> updateImageSortOrder(
            Integer id, Integer imageId, UpdateProductImageSortOrderRequest request) {
        var updated = productService.updateImageSortOrder(id, imageId, request.getSortOrder());
        return ResponseEntity.ok(productMapper.toImageResponse(updated));
    }

    @Override
    public ResponseEntity<ProductResponse> getById(Integer id) {
        return ResponseEntity.ok(productMapper.toResponse(productService.getById(id)));
    }

    @Override
    public ResponseEntity<PagedResponse<ProductResponse>> list(
            int page, int size, String sortBy, String sortDir,
            Integer productCategoryId, Boolean active, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        Page<ProductResponse> responses = productService.list(pageable, productCategoryId, active, q)
                .map(productMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    private static List<ProductCommands.VariantInput> toVariantInputs(List<ProductVariantRequest> requests) {
        return requests.stream()
                .map(r -> new ProductCommands.VariantInput(r.getSku(), r.getPrice(), r.getStockQuantity(), r.getAttributes()))
                .toList();
    }

    private static List<ProductCommands.ImageInput> toImageInputs(List<ProductImageRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(r -> new ProductCommands.ImageInput(r.getStorageKey(), r.getSortOrder()))
                .toList();
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
