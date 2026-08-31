package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.api.ProductTagApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.CreateProductTagRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductTagResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateProductTagRequest;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductTag;
import com.ttg.devknowledgeplatform.ecommerce.mapper.ProductTagMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductTagService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Implementation of {@link ProductTagApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ProductTagController implements ProductTagApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "dteCreation");

    private final ProductTagService productTagService;
    private final ProductTagMapper productTagMapper;

    @Override
    public ResponseEntity<ProductTagResponse> create(CreateProductTagRequest request) {
        ProductTag tag = productTagService.create(request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(productTagMapper.toResponse(tag));
    }

    @Override
    public ResponseEntity<ProductTagResponse> update(Integer id, UpdateProductTagRequest request) {
        ProductTag tag = productTagService.update(id, request.getName());
        return ResponseEntity.ok(productTagMapper.toResponse(tag));
    }

    @Override
    public ResponseEntity<Void> delete(Integer id) {
        productTagService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ProductTagResponse> getById(Integer id) {
        return ResponseEntity.ok(productTagMapper.toResponse(productTagService.getById(id)));
    }

    @Override
    public ResponseEntity<PagedResponse<ProductTagResponse>> list(int page, int size, String sortBy, String sortDir, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        Page<ProductTagResponse> responses = productTagService.list(pageable, q).map(productTagMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
