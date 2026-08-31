package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CartServiceImpl} — currently just the bulk {@link CartServiceImpl#removeItems}
 * path added alongside the GUI's multi-select delete/checkout feature.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    private static final String USER_UUID = "user-uuid-1";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private CartServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CartServiceImpl(redisTemplate, productVariantRepository);
        ReflectionTestUtils.setField(service, "cartTtl", Duration.ofDays(30));
    }

    @Test
    void removeItems_deletesEveryHashFieldInOneCallAndRefreshesTtl() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        service.removeItems(USER_UUID, List.of(1, 2, 3));

        verify(hashOperations).delete("cart:" + USER_UUID, "1", "2", "3");
        verify(redisTemplate).expire("cart:" + USER_UUID, Duration.ofDays(30));
    }
}
