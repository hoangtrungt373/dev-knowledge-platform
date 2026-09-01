package com.ttg.devknowledgeplatform.ecommerce.api.impl;

import com.ttg.devknowledgeplatform.ecommerce.api.CouponImageApi;
import com.ttg.devknowledgeplatform.ecommerce.dto.CouponImageResponse;
import com.ttg.devknowledgeplatform.ecommerce.service.CouponImageService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Implementation of {@link CouponImageApi}.
 */
@RestController
@RequiredArgsConstructor
public class CouponImageController implements CouponImageApi {

    private final CouponImageService couponImageService;

    @Override
    public ResponseEntity<CouponImageResponse> upload(MultipartFile file) {
        String url = couponImageService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(CouponImageResponse.builder().url(url).build());
    }
}
