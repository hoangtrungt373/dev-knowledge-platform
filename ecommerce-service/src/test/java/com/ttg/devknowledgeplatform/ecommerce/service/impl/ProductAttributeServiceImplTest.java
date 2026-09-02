package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttribute;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttributeValue;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductAttributeRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryAttributeRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductAttributeServiceImpl} — mirrors {@code ProductTagServiceImplTest}'s
 * shape (this module's own established convention for a flat CRUD entity), plus the
 * {@code values} list management {@link ProductAttribute} has and {@link
 * com.ttg.devknowledgeplatform.ecommerce.entity.ProductTag} doesn't.
 */
@ExtendWith(MockitoExtension.class)
class ProductAttributeServiceImplTest {

    @Mock
    private ProductAttributeRepository productAttributeRepository;

    @Mock
    private ProductCategoryAttributeRepository productCategoryAttributeRepository;

    @InjectMocks
    private ProductAttributeServiceImpl service;

    private ProductAttribute existing;

    @BeforeEach
    void setUp() {
        existing = new ProductAttribute();
        existing.setId(1);
        existing.setName("color");
    }

    @Nested
    class Create {

        @Test
        void createsAttributeWithValuesInListOrder() {
            when(productAttributeRepository.existsByNameIgnoreCase("size")).thenReturn(false);
            when(productAttributeRepository.save(any(ProductAttribute.class))).thenAnswer(invocation -> {
                ProductAttribute saved = invocation.getArgument(0);
                saved.setId(2);
                return saved;
            });

            ProductAttribute result = service.create("size", List.of("S", "M", "L"));

            assertThat(result.getId()).isEqualTo(2);
            assertThat(result.getName()).isEqualTo("size");
            assertThat(result.getValues()).extracting("value").containsExactly("S", "M", "L");
            assertThat(result.getValues()).extracting("displayOrder").containsExactly(0, 1, 2);
            assertThat(result.getValues()).allMatch(v -> v.getAttribute() == result);
        }

        @Test
        void trimsNameAndValuesBeforeSaving() {
            when(productAttributeRepository.existsByNameIgnoreCase("size")).thenReturn(false);
            when(productAttributeRepository.save(any(ProductAttribute.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductAttribute result = service.create("  size  ", List.of("  S  ", "M"));

            assertThat(result.getName()).isEqualTo("size");
            assertThat(result.getValues()).extracting("value").containsExactly("S", "M");
        }

        @Test
        void rejectsNameThatAlreadyExists() {
            when(productAttributeRepository.existsByNameIgnoreCase("color")).thenReturn(true);

            assertThatThrownBy(() -> service.create("color", List.of("Red")))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_ATTRIBUTE_NAME_CONFLICT);

            verify(productAttributeRepository, never()).save(any());
        }

        @Test
        void rejectsAnEmptyValueList() {
            when(productAttributeRepository.existsByNameIgnoreCase("size")).thenReturn(false);
            when(productAttributeRepository.save(any(ProductAttribute.class))).thenAnswer(invocation -> invocation.getArgument(0));

            assertThatThrownBy(() -> service.create("size", List.of()))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_ATTRIBUTE_VALUES_REQUIRED);
        }

        @Test
        void rejectsADuplicateValueCaseInsensitively() {
            when(productAttributeRepository.existsByNameIgnoreCase("size")).thenReturn(false);
            when(productAttributeRepository.save(any(ProductAttribute.class))).thenAnswer(invocation -> invocation.getArgument(0));

            assertThatThrownBy(() -> service.create("size", List.of("S", "s")))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_ATTRIBUTE_VALUE_DUPLICATE);
        }
    }

    @Nested
    class Update {

        @Test
        void renamesAttribute() {
            when(productAttributeRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productAttributeRepository.existsByNameIgnoreCaseAndIdNot("shade", 1)).thenReturn(false);
            when(productAttributeRepository.save(any(ProductAttribute.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductAttribute result = service.update(1, "shade", List.of("Red", "Blue"));

            assertThat(result.getName()).isEqualTo("shade");
        }

        @Test
        void replacesTheEntireValueList() {
            existing.getValues().add(new ProductAttributeValue());
            existing.getValues().get(0).setAttribute(existing);
            existing.getValues().get(0).setValue("Red");
            existing.getValues().get(0).setDisplayOrder(0);
            when(productAttributeRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productAttributeRepository.save(any(ProductAttribute.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductAttribute result = service.update(1, "color", List.of("Blue", "Black"));

            assertThat(result.getValues()).extracting("value").containsExactly("Blue", "Black");
        }

        @Test
        void rejectsRenameToAnAlreadyUsedName() {
            when(productAttributeRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productAttributeRepository.existsByNameIgnoreCaseAndIdNot("size", 1)).thenReturn(true);

            assertThatThrownBy(() -> service.update(1, "size", List.of("Red")))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_ATTRIBUTE_NAME_CONFLICT);
        }

        @Test
        void throwsWhenAttributeDoesNotExist() {
            when(productAttributeRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99, "size", List.of("Red")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class GetById {

        @Test
        void returnsAttributeWhenFound() {
            when(productAttributeRepository.findById(1)).thenReturn(Optional.of(existing));

            assertThat(service.getById(1)).isEqualTo(existing);
        }

        @Test
        void throwsWhenNotFound() {
            when(productAttributeRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class ListAttributes {

        @Test
        void delegatesToRepositoryWithSpecAndPageable() {
            PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));
            when(productAttributeRepository.findAll(any(Specification.class), eq(pageable)))
                    .thenReturn(new PageImpl<>(List.of(existing)));

            Page<ProductAttribute> result = service.list(pageable, "col");

            assertThat(result.getContent()).containsExactly(existing);
        }
    }

    @Nested
    class Delete {

        @Test
        void deletesAttributeWhenNotAssignedToAnyCategory() {
            when(productAttributeRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryAttributeRepository.existsByAttributeId(1)).thenReturn(false);

            service.delete(1);

            // Cast needed — see ProductTagServiceImplTest's own identical note: ProductAttributeRepository
            // extends both JpaRepository (delete(T)) and JpaSpecificationExecutor, so javac can't
            // disambiguate `.delete(existing)` inside a verify() chain otherwise.
            verify((JpaRepository<ProductAttribute, Integer>) productAttributeRepository).delete(existing);
        }

        @Test
        void rejectsDeletingAnAttributeStillAssignedToACategory() {
            when(productAttributeRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryAttributeRepository.existsByAttributeId(1)).thenReturn(true);

            assertThatThrownBy(() -> service.delete(1))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_ATTRIBUTE_IN_USE);

            verify((JpaRepository<ProductAttribute, Integer>) productAttributeRepository, never()).delete(any(ProductAttribute.class));
        }

        @Test
        void throwsWhenAttributeDoesNotExist() {
            when(productAttributeRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(99))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
