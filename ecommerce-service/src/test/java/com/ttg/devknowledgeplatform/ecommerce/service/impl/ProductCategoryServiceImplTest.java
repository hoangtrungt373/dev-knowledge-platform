package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryRepository;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductCategoryServiceImpl} — covers Epic 1's "Catalog Management (Admin)"
 * category surface (the read side of US-1.1's category browsing depends on these rows existing).
 */
@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceImplTest {

    @Mock
    private ProductCategoryRepository productCategoryRepository;

    @Mock
    private SlugService slugService;

    @InjectMocks
    private ProductCategoryServiceImpl service;

    private ProductCategory existing;

    @BeforeEach
    void setUp() {
        existing = new ProductCategory();
        existing.setId(1);
        existing.setName("Apparel");
        existing.setSlug("apparel");
    }

    @Nested
    class Create {

        @Test
        void createsCategoryWhenNameIsAvailable() {
            when(productCategoryRepository.existsByNameIgnoreCase("Drinkware")).thenReturn(false);
            when(slugService.generateUniqueSlug(eq("Drinkware"), any(), eq(EcommerceErrorCode.PRODUCT_CATEGORY_SLUG_CONFLICT)))
                    .thenReturn("drinkware");
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> {
                ProductCategory saved = invocation.getArgument(0);
                saved.setId(2);
                return saved;
            });

            ProductCategory result = service.create("Drinkware");

            assertThat(result.getId()).isEqualTo(2);
            assertThat(result.getName()).isEqualTo("Drinkware");
            assertThat(result.getSlug()).isEqualTo("drinkware");
        }

        @Test
        void trimsNameBeforeCheckingAndSaving() {
            when(productCategoryRepository.existsByNameIgnoreCase("Drinkware")).thenReturn(false);
            when(slugService.generateUniqueSlug(anyString(), any(), any())).thenReturn("drinkware");
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCategory result = service.create("  Drinkware  ");

            assertThat(result.getName()).isEqualTo("Drinkware");
        }

        @Test
        void rejectsNameThatAlreadyExists() {
            when(productCategoryRepository.existsByNameIgnoreCase("Apparel")).thenReturn(true);

            assertThatThrownBy(() -> service.create("Apparel"))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_CATEGORY_NAME_CONFLICT);

            verify(productCategoryRepository, never()).save(any());
        }
    }

    @Nested
    class Update {

        @Test
        void renamesAndRegeneratesSlugWhenNameChanges() {
            when(productCategoryRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.existsByNameIgnoreCaseAndIdNot("Drinkware", 1)).thenReturn(false);
            when(slugService.generateUniqueSlug(eq("Drinkware"), any(), eq(1), eq(EcommerceErrorCode.PRODUCT_CATEGORY_SLUG_CONFLICT)))
                    .thenReturn("drinkware");
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCategory result = service.update(1, "Drinkware");

            assertThat(result.getName()).isEqualTo("Drinkware");
            assertThat(result.getSlug()).isEqualTo("drinkware");
        }

        @Test
        void skipsSlugRegenerationWhenNameIsUnchanged() {
            when(productCategoryRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCategory result = service.update(1, "apparel"); // same name, different case

            assertThat(result.getSlug()).isEqualTo("apparel"); // unchanged
            verify(slugService, never()).generateUniqueSlug(anyString(), any(), any(), any());
        }

        @Test
        void rejectsRenameToAnAlreadyUsedName() {
            when(productCategoryRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.existsByNameIgnoreCaseAndIdNot("Drinkware", 1)).thenReturn(true);

            assertThatThrownBy(() -> service.update(1, "Drinkware"))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_CATEGORY_NAME_CONFLICT);
        }

        @Test
        void throwsWhenCategoryDoesNotExist() {
            when(productCategoryRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99, "Drinkware"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class GetById {

        @Test
        void returnsCategoryWhenFound() {
            when(productCategoryRepository.findById(1)).thenReturn(Optional.of(existing));

            assertThat(service.getById(1)).isEqualTo(existing);
        }

        @Test
        void throwsWhenNotFound() {
            when(productCategoryRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class ListCategories {

        @Test
        void sortsByNameAscending() {
            when(productCategoryRepository.findAll(any(Specification.class), eq(Sort.by(Sort.Direction.ASC, "name"))))
                    .thenReturn(List.of(existing));

            List<ProductCategory> result = service.list("app");

            assertThat(result).containsExactly(existing);
        }
    }
}
