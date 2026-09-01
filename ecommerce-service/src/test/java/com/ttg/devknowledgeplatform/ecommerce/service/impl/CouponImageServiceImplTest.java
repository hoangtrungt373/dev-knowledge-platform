package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.infra.service.StorageService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponImageServiceImplTest {

    @Mock
    private StorageService storageService;

    @InjectMocks
    private CouponImageServiceImpl service;

    @Test
    void delegatesToStorageServicesPublicUploadAndReturnsThePermanentUrl() {
        MultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", new byte[] {1, 2, 3});
        when(storageService.uploadPublicImage(anyString(), any(MultipartFile.class)))
                .thenReturn("http://localhost:9000/product-images/description-images/some-uuid.png");

        String result = service.upload(file);

        assertThat(result).isEqualTo("http://localhost:9000/product-images/description-images/some-uuid.png");
    }
}
