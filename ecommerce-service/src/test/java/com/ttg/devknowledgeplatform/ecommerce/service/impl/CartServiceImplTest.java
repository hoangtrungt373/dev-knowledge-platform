package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductVariantRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.Cart;
import com.ttg.devknowledgeplatform.ecommerce.service.CartLine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CartServiceImpl} — the bulk {@link CartServiceImpl#removeItems} path added
 * alongside the GUI's multi-select delete/checkout feature, and {@link CartServiceImpl#getCart}
 * (previously untested at all, despite being the app's single hottest read path — every cart view
 * *and* every checkout {@code preview}/{@code confirm} call).
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

    @Nested
    class GetCart {

        private static ProductVariant variant(Integer id, boolean productActive) {
            Product product = new Product();
            product.setActive(productActive);
            ProductVariant variant = new ProductVariant();
            variant.setId(id);
            variant.setProduct(product);
            return variant;
        }

        @Test
        void returnsAnEmptyCartWithoutQueryingTheRepositoryWhenTheHashIsEmpty() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("cart:" + USER_UUID)).thenReturn(Map.of());

            Cart result = service.getCart(USER_UUID);

            assertThat(result.lines()).isEmpty();
            verify(productVariantRepository, never()).findAllByIdWithProduct(anyCollection());
        }

        @Test
        void resolvesAnAvailableLineFromTheBatchFetchedVariant() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("cart:" + USER_UUID)).thenReturn(Map.of("5", "2"));
            ProductVariant variant = variant(5, true);
            when(productVariantRepository.findAllByIdWithProduct(List.of(5))).thenReturn(List.of(variant));

            Cart result = service.getCart(USER_UUID);

            assertThat(result.lines()).hasSize(1);
            CartLine line = result.lines().get(0);
            assertThat(line.available()).isTrue();
            assertThat(line.variantId()).isEqualTo(5);
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.variant()).isSameAs(variant);
        }

        @Test
        void resolvesAnUnavailableLineWhenTheVariantsProductIsInactive() {
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("cart:" + USER_UUID)).thenReturn(Map.of("5", "2"));
            when(productVariantRepository.findAllByIdWithProduct(List.of(5))).thenReturn(List.of(variant(5, false)));

            Cart result = service.getCart(USER_UUID);

            CartLine line = result.lines().get(0);
            assertThat(line.available()).isFalse();
            assertThat(line.variantId()).isEqualTo(5);
            assertThat(line.quantity()).isEqualTo(2);
            assertThat(line.variant()).isNull();
        }

        @Test
        void resolvesAnUnavailableLineWhenTheVariantNoLongerExistsAtAll() {
            // The variant was hard-deleted (ProductServiceImpl.removeVariant) — simply absent from
            // the batch-fetch result, not represented as an exception or a null list entry.
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("cart:" + USER_UUID)).thenReturn(Map.of("5", "2"));
            when(productVariantRepository.findAllByIdWithProduct(List.of(5))).thenReturn(List.of());

            Cart result = service.getCart(USER_UUID);

            CartLine line = result.lines().get(0);
            assertThat(line.available()).isFalse();
            assertThat(line.variant()).isNull();
        }

        @Test
        void resolvesEveryLineWithExactlyOneBatchQueryRegardlessOfCartSize() {
            // The actual N+1 fix: a 3-line cart used to cost up to 6 queries (one findById + one
            // lazy product load per line) — must now cost exactly one query total.
            Map<Object, Object> raw = new LinkedHashMap<>();
            raw.put("1", "1");
            raw.put("2", "3");
            raw.put("3", "1");
            when(redisTemplate.opsForHash()).thenReturn(hashOperations);
            when(hashOperations.entries("cart:" + USER_UUID)).thenReturn(raw);
            when(productVariantRepository.findAllByIdWithProduct(anyCollection()))
                    .thenReturn(List.of(variant(1, true), variant(2, true), variant(3, true)));

            Cart result = service.getCart(USER_UUID);

            assertThat(result.lines()).hasSize(3);
            verify(productVariantRepository, times(1)).findAllByIdWithProduct(anyCollection());
            verify(productVariantRepository, never()).findById(org.mockito.ArgumentMatchers.any());
        }
    }
}
