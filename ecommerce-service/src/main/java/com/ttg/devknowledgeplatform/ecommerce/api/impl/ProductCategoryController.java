package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.ecommerce.api.ProductCategoryApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.CreateProductCategoryRequest;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductCategoryResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.UpdateProductCategoryRequest;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.mapper.ProductCategoryMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCategoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Implementation of {@link ProductCategoryApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ProductCategoryController implements ProductCategoryApi {

    private final ProductCategoryService productCategoryService;
    private final ProductCategoryMapper productCategoryMapper;

    @Override
    public ResponseEntity<ProductCategoryResponse> create(CreateProductCategoryRequest request) {
        ProductCategory category = productCategoryService.create(request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(productCategoryMapper.toResponse(category));
    }

    @Override
    public ResponseEntity<ProductCategoryResponse> update(Integer id, UpdateProductCategoryRequest request) {
        ProductCategory category = productCategoryService.update(id, request.getName());
        return ResponseEntity.ok(productCategoryMapper.toResponse(category));
    }

    @Override
    public ResponseEntity<ProductCategoryResponse> getById(Integer id) {
        return ResponseEntity.ok(productCategoryMapper.toResponse(productCategoryService.getById(id)));
    }

    @Override
    public ResponseEntity<List<ProductCategoryResponse>> list(String q) {
        List<ProductCategoryResponse> categories = productCategoryService.list(q).stream()
                .map(productCategoryMapper::toResponse)
                .toList();
        return ResponseEntity.ok(categories);
    }
}
