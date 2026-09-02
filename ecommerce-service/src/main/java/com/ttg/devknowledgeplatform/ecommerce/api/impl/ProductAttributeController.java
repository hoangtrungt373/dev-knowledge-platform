package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.ecommerce.api.ProductAttributeApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.CreateProductAttributeRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductAttributeResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateProductAttributeRequest;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttribute;
import com.ttg.devknowledgeplatform.ecommerce.mapper.ProductAttributeMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductAttributeService;

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
 * Implementation of {@link ProductAttributeApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ProductAttributeController implements ProductAttributeApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "dteCreation");

    private final ProductAttributeService productAttributeService;
    private final ProductAttributeMapper productAttributeMapper;

    @Override
    public ResponseEntity<ProductAttributeResponse> create(CreateProductAttributeRequest request) {
        ProductAttribute attribute = productAttributeService.create(request.getName(), request.getValues());
        return ResponseEntity.status(HttpStatus.CREATED).body(productAttributeMapper.toResponse(attribute));
    }

    @Override
    public ResponseEntity<ProductAttributeResponse> update(Integer id, UpdateProductAttributeRequest request) {
        ProductAttribute attribute = productAttributeService.update(id, request.getName(), request.getValues());
        return ResponseEntity.ok(productAttributeMapper.toResponse(attribute));
    }

    @Override
    public ResponseEntity<Void> delete(Integer id) {
        productAttributeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ProductAttributeResponse> getById(Integer id) {
        return ResponseEntity.ok(productAttributeMapper.toResponse(productAttributeService.getById(id)));
    }

    @Override
    public ResponseEntity<PagedResponse<ProductAttributeResponse>> list(int page, int size, String sortBy, String sortDir, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        Page<ProductAttributeResponse> responses = productAttributeService.list(pageable, q).map(productAttributeMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
