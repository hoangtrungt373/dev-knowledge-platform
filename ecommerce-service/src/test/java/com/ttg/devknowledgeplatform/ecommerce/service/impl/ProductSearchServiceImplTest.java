package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductSearchView;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductSearchViewRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductSearchServiceImpl} — the thin service layer in front of
 * {@link ProductSearchViewRepository}'s native search query (US-1.3/US-1.4). The actual
 * {@code tsvector}/{@code pg_trgm}/JSONB-containment matching is real Postgres SQL that can't be
 * meaningfully unit tested against a mock — see {@code ProductSearchViewRepositoryIT} for that.
 * This class covers what a mock genuinely can verify: blank-{@code q} handling, the combined
 * attribute-filter JSON this class builds before it ever reaches the repository, and that the
 * {@link Pageable} passed through is unsorted (the native query bakes in its own {@code ORDER BY}).
 */
@ExtendWith(MockitoExtension.class)
class ProductSearchServiceImplTest {

    @Mock
    private ProductSearchViewRepository productSearchViewRepository;

    private ProductSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        // A real ObjectMapper, not mocked — the attribute-filter JSON's exact shape is the thing
        // under test, and a mock would just echo back whatever we told it to.
        service = new ProductSearchServiceImpl(productSearchViewRepository, new ObjectMapper());
    }

    private Page<ProductSearchView> emptyPage() {
        return new PageImpl<>(List.of());
    }

    @Test
    void blankKeywordIsPassedAsNullToTheRepository() {
        when(productSearchViewRepository.search(any(), isNull(), any(), any(), anyBoolean(), any(), anyDouble(), any()))
                .thenReturn(emptyPage());

        service.search(0, 20, null, "   ", null, null, false, Map.of());

        verify(productSearchViewRepository).search(
                isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), anyDouble(), any(Pageable.class));
    }

    @Test
    void nonBlankKeywordIsPassedThrough() {
        when(productSearchViewRepository.search(any(), eq("hoodie"), any(), any(), anyBoolean(), any(), anyDouble(), any()))
                .thenReturn(emptyPage());

        service.search(0, 20, 5, "hoodie", BigDecimal.TEN, BigDecimal.valueOf(100), true, Map.of());

        verify(productSearchViewRepository).search(
                eq(5), eq("hoodie"), eq(BigDecimal.TEN), eq(BigDecimal.valueOf(100)), eq(true), isNull(), anyDouble(), any());
    }

    @Test
    void emptyAttributeFiltersProduceNoJsonFilter() {
        when(productSearchViewRepository.search(any(), any(), any(), any(), anyBoolean(), any(), anyDouble(), any()))
                .thenReturn(emptyPage());

        service.search(0, 20, null, null, null, null, false, Map.of());

        verify(productSearchViewRepository).search(any(), any(), any(), any(), anyBoolean(), isNull(), anyDouble(), any());
    }

    @Test
    void attributeFiltersAreCombinedIntoOneJsonObjectWithEachValueAsASingletonArray() {
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        when(productSearchViewRepository.search(any(), any(), any(), any(), anyBoolean(), jsonCaptor.capture(), anyDouble(), any()))
                .thenReturn(emptyPage());

        service.search(0, 20, null, null, null, null, false, Map.of("size", "M", "color", "Blue"));

        String json = jsonCaptor.getValue();
        assertThat(json).contains("\"size\":[\"M\"]", "\"color\":[\"Blue\"]");
    }

    @Test
    void pageableIsUnsortedSinceTheNativeQueryOwnsItsOwnOrdering() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(productSearchViewRepository.search(any(), any(), any(), any(), anyBoolean(), any(), anyDouble(), pageableCaptor.capture()))
                .thenReturn(emptyPage());

        service.search(2, 15, null, null, null, null, false, Map.of());

        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getSort().isUnsorted()).isTrue();
        assertThat(captured).isEqualTo(PageRequest.of(2, 15));
    }
}
