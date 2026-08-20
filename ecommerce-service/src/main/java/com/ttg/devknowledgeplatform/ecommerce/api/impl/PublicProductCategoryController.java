package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.ecommerce.api.PublicProductCategoryApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductCategoryResponse;
import com.ttg.devknowledgeplatform.ecommerce.mapper.ProductCategoryMapper;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCategoryService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Implementation of {@link PublicProductCategoryApi}.
 */
@RestController
@RequiredArgsConstructor
public class PublicProductCategoryController implements PublicProductCategoryApi {

    private final ProductCategoryService productCategoryService;
    private final ProductCategoryMapper productCategoryMapper;

    @Override
    public ResponseEntity<List<ProductCategoryResponse>> list() {
        List<ProductCategoryResponse> categories = productCategoryService.list(null).stream()
                .map(productCategoryMapper::toResponse)
                .toList();
        return ResponseEntity.ok(categories);
    }
}
