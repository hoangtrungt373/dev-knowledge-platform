package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductTag;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductTagAssignmentRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductTagRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

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
 * Unit tests for {@link ProductTagServiceImpl} — mirrors {@code ProductCategoryServiceImplTest}'s
 * shape (this module's own established convention for a flat CRUD entity), minus anything
 * hierarchy-related, since {@link ProductTag} has none.
 */
@ExtendWith(MockitoExtension.class)
class ProductTagServiceImplTest {

    @Mock
    private ProductTagRepository productTagRepository;

    @Mock
    private ProductTagAssignmentRepository productTagAssignmentRepository;

    @Mock
    private SlugService slugService;

    @InjectMocks
    private ProductTagServiceImpl service;

    private ProductTag existing;

    @BeforeEach
    void setUp() {
        existing = new ProductTag();
        existing.setId(1);
        existing.setName("New Arrival");
        existing.setSlug("new-arrival");
    }

    @Nested
    class Create {

        @Test
        void createsTagWhenNameIsAvailable() {
            when(productTagRepository.existsByNameIgnoreCase("Best Seller")).thenReturn(false);
            when(slugService.generateUniqueSlug(eq("Best Seller"), any(), eq(EcommerceErrorCode.PRODUCT_TAG_SLUG_CONFLICT)))
                    .thenReturn("best-seller");
            when(productTagRepository.save(any(ProductTag.class))).thenAnswer(invocation -> {
                ProductTag saved = invocation.getArgument(0);
                saved.setId(2);
                return saved;
            });

            ProductTag result = service.create("Best Seller");

            assertThat(result.getId()).isEqualTo(2);
            assertThat(result.getName()).isEqualTo("Best Seller");
            assertThat(result.getSlug()).isEqualTo("best-seller");
        }

        @Test
        void trimsNameBeforeCheckingAndSaving() {
            when(productTagRepository.existsByNameIgnoreCase("Best Seller")).thenReturn(false);
            when(slugService.generateUniqueSlug(anyString(), any(), any())).thenReturn("best-seller");
            when(productTagRepository.save(any(ProductTag.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductTag result = service.create("  Best Seller  ");

            assertThat(result.getName()).isEqualTo("Best Seller");
        }

        @Test
        void rejectsNameThatAlreadyExists() {
            when(productTagRepository.existsByNameIgnoreCase("New Arrival")).thenReturn(true);

            assertThatThrownBy(() -> service.create("New Arrival"))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_TAG_NAME_CONFLICT);

            verify(productTagRepository, never()).save(any());
        }
    }

    @Nested
    class Update {

        @Test
        void renamesAndRegeneratesSlugWhenNameChanges() {
            when(productTagRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productTagRepository.existsByNameIgnoreCaseAndIdNot("Best Seller", 1)).thenReturn(false);
            when(slugService.generateUniqueSlug(eq("Best Seller"), any(), eq(1), eq(EcommerceErrorCode.PRODUCT_TAG_SLUG_CONFLICT)))
                    .thenReturn("best-seller");
            when(productTagRepository.save(any(ProductTag.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductTag result = service.update(1, "Best Seller");

            assertThat(result.getName()).isEqualTo("Best Seller");
            assertThat(result.getSlug()).isEqualTo("best-seller");
        }

        @Test
        void skipsSlugRegenerationWhenNameIsUnchanged() {
            when(productTagRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productTagRepository.save(any(ProductTag.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductTag result = service.update(1, "new arrival"); // same name, different case

            assertThat(result.getSlug()).isEqualTo("new-arrival"); // unchanged
            verify(slugService, never()).generateUniqueSlug(anyString(), any(), any(), any());
        }

        @Test
        void rejectsRenameToAnAlreadyUsedName() {
            when(productTagRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productTagRepository.existsByNameIgnoreCaseAndIdNot("Best Seller", 1)).thenReturn(true);

            assertThatThrownBy(() -> service.update(1, "Best Seller"))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_TAG_NAME_CONFLICT);
        }

        @Test
        void throwsWhenTagDoesNotExist() {
            when(productTagRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99, "Best Seller"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class GetById {

        @Test
        void returnsTagWhenFound() {
            when(productTagRepository.findById(1)).thenReturn(Optional.of(existing));

            assertThat(service.getById(1)).isEqualTo(existing);
        }

        @Test
        void throwsWhenNotFound() {
            when(productTagRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class ListTags {

        @Test
        void delegatesToRepositoryWithSpecAndPageable() {
            PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));
            when(productTagRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(existing)));

            Page<ProductTag> result = service.list(pageable, "new");

            assertThat(result.getContent()).containsExactly(existing);
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesTagWhenNotAssignedToAnyProduct() {
            when(productTagRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productTagAssignmentRepository.existsByProductTagId(1)).thenReturn(false);

            service.delete(1);

            // Cast needed: ProductTagRepository extends both JpaRepository (delete(T)) and
            // JpaSpecificationExecutor (which gained its own delete(Specification<T>) in recent
            // Spring Data JPA) — javac can't disambiguate `.delete(existing)` between the two
            // inherited overloads inside a verify() chain otherwise (a real ambiguity, not a
            // Mockito quirk — production code's own plain `productTagRepository.delete(tag)` call
            // in ProductTagServiceImpl has no such issue, since the reference isn't the return
            // value of a generic verify() call there).
            verify((JpaRepository<ProductTag, Integer>) productTagRepository).delete(existing);
        }

        @Test
        void rejectsDeletingATagStillAssignedToAProduct() {
            when(productTagRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productTagAssignmentRepository.existsByProductTagId(1)).thenReturn(true);

            assertThatThrownBy(() -> service.delete(1))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_TAG_IN_USE);

            verify((JpaRepository<ProductTag, Integer>) productTagRepository, never()).delete(any(ProductTag.class));
        }

        @Test
        void throwsWhenTagDoesNotExist() {
            when(productTagRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
