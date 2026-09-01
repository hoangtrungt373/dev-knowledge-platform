package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.ecommerce.service.CouponImageService;
import com.ttg.devknowledgeplatform.infra.service.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponImageServiceImpl implements CouponImageService {

    private final StorageService storageService;

    @Override
    public String upload(MultipartFile file) {
        String url = storageService.uploadPublicImage(UUID.randomUUID().toString(), file);
        log.info("Uploaded coupon image");
        return url;
    }
}
