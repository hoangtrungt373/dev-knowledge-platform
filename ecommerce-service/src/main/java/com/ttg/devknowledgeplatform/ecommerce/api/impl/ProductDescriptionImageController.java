package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.ecommerce.api.ProductDescriptionImageApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductDescriptionImageResponse;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductDescriptionImageService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implementation of {@link ProductDescriptionImageApi}.
 */
@RestController
@RequiredArgsConstructor
public class ProductDescriptionImageController implements ProductDescriptionImageApi {

    private final ProductDescriptionImageService productDescriptionImageService;

    @Override
    public ResponseEntity<ProductDescriptionImageResponse> upload(MultipartFile file) {
        String url = productDescriptionImageService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductDescriptionImageResponse.builder().url(url).build());
    }
}
