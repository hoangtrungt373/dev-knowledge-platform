package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttribute;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategoryAttribute;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductAttributeRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryRepository;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCategoryService;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCategoryTreeNode;
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
    private ProductAttributeRepository productAttributeRepository;

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

            ProductCategory result = service.create("Drinkware", null, null);

            assertThat(result.getId()).isEqualTo(2);
            assertThat(result.getName()).isEqualTo("Drinkware");
            assertThat(result.getSlug()).isEqualTo("drinkware");
            assertThat(result.getParent()).isNull();
        }

        @Test
        void trimsNameBeforeCheckingAndSaving() {
            when(productCategoryRepository.existsByNameIgnoreCase("Drinkware")).thenReturn(false);
            when(slugService.generateUniqueSlug(anyString(), any(), any())).thenReturn("drinkware");
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCategory result = service.create("  Drinkware  ", null, null);

            assertThat(result.getName()).isEqualTo("Drinkware");
        }

        @Test
        void rejectsNameThatAlreadyExists() {
            when(productCategoryRepository.existsByNameIgnoreCase("Apparel")).thenReturn(true);

            assertThatThrownBy(() -> service.create("Apparel", null, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_CATEGORY_NAME_CONFLICT);

            verify(productCategoryRepository, never()).save(any());
        }

        @Test
        void createsCategoryUnderAnExistingParent() {
            when(productCategoryRepository.existsByNameIgnoreCase("Outdoor furniture")).thenReturn(false);
            when(productCategoryRepository.findById(1)).thenReturn(Optional.of(existing));
            when(slugService.generateUniqueSlug(eq("Outdoor furniture"), any(), eq(EcommerceErrorCode.PRODUCT_CATEGORY_SLUG_CONFLICT)))
                    .thenReturn("outdoor-furniture");
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCategory result = service.create("Outdoor furniture", 1, null);

            assertThat(result.getParent()).isEqualTo(existing);
        }

        @Test
        void rejectsAParentIdThatDoesNotExist() {
            when(productCategoryRepository.existsByNameIgnoreCase("Outdoor furniture")).thenReturn(false);
            when(productCategoryRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create("Outdoor furniture", 99, null))
                    .isInstanceOf(ResourceNotFoundException.class);

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

            ProductCategory result = service.update(1, "Drinkware", null, null);

            assertThat(result.getName()).isEqualTo("Drinkware");
            assertThat(result.getSlug()).isEqualTo("drinkware");
        }

        @Test
        void skipsSlugRegenerationWhenNameIsUnchanged() {
            when(productCategoryRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCategory result = service.update(1, "apparel", null, null); // same name, different case

            assertThat(result.getSlug()).isEqualTo("apparel"); // unchanged
            verify(slugService, never()).generateUniqueSlug(anyString(), any(), any(), any());
        }

        @Test
        void rejectsRenameToAnAlreadyUsedName() {
            when(productCategoryRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.existsByNameIgnoreCaseAndIdNot("Drinkware", 1)).thenReturn(true);

            assertThatThrownBy(() -> service.update(1, "Drinkware", null, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_CATEGORY_NAME_CONFLICT);
        }

        @Test
        void throwsWhenCategoryDoesNotExist() {
            when(productCategoryRepository.findById(99)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99, "Drinkware", null, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        void reassignsParent() {
            ProductCategory newParent = new ProductCategory();
            newParent.setId(2);
            newParent.setName("Furniture");
            when(productCategoryRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.findById(2)).thenReturn(Optional.of(newParent));
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCategory result = service.update(1, "Apparel", 2, null);

            assertThat(result.getParent()).isEqualTo(newParent);
        }

        @Test
        void rejectsSettingItselfAsParent() {
            when(productCategoryRepository.findById(1)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.update(1, "Apparel", 1, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_CATEGORY_CYCLIC_PARENT);

            verify(productCategoryRepository, never()).save(any());
        }

        @Test
        void rejectsSettingADescendantAsParent() {
            // existing(1) -> child(2) -> grandchild(3); reassigning existing's parent to its own
            // grandchild would create a cycle.
            ProductCategory child = new ProductCategory();
            child.setId(2);
            child.setName("Child");
            child.setParent(existing);

            ProductCategory grandchild = new ProductCategory();
            grandchild.setId(3);
            grandchild.setName("Grandchild");
            grandchild.setParent(child);

            when(productCategoryRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.findById(3)).thenReturn(Optional.of(grandchild));

            assertThatThrownBy(() -> service.update(1, "Apparel", 3, null))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_CATEGORY_CYCLIC_PARENT);

            verify(productCategoryRepository, never()).save(any());
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

    @Nested
    class ListTree {

        @Test
        void groupsChildrenUnderTheirParentAndSortsEachLevelByName() {
            ProductCategory furniture = new ProductCategory();
            furniture.setId(1);
            furniture.setName("Furniture");

            ProductCategory outdoor = new ProductCategory();
            outdoor.setId(2);
            outdoor.setName("Outdoor furniture");
            outdoor.setParent(furniture);

            ProductCategory apparel = new ProductCategory();
            apparel.setId(3);
            apparel.setName("Apparel");

            when(productCategoryRepository.findAll()).thenReturn(List.of(furniture, outdoor, apparel));

            List<ProductCategoryTreeNode> roots = service.listTree();

            assertThat(roots).extracting(n -> n.category().getName()).containsExactly("Apparel", "Furniture");
            ProductCategoryTreeNode furnitureNode = roots.get(1);
            assertThat(furnitureNode.children()).extracting(n -> n.category().getName()).containsExactly("Outdoor furniture");
        }

        @Test
        void treatsAnOrphanedChildAsARootRatherThanDroppingIt() {
            // Defensive: a child whose parent id was somehow deleted out from under it (shouldn't
            // happen given the FK, but the tree-building loop must not silently lose the row).
            ProductCategory orphan = new ProductCategory();
            orphan.setId(2);
            orphan.setName("Orphan");
            ProductCategory missingParent = new ProductCategory();
            missingParent.setId(99);
            orphan.setParent(missingParent);

            when(productCategoryRepository.findAll()).thenReturn(List.of(orphan));

            List<ProductCategoryTreeNode> roots = service.listTree();

            assertThat(roots).extracting(n -> n.category().getName()).containsExactly("Orphan");
        }
    }

    @Nested
    class CategoryAttributes {

        private ProductAttribute color;
        private ProductAttribute size;

        @BeforeEach
        void setUpAttributes() {
            color = new ProductAttribute();
            color.setId(10);
            color.setName("color");
            size = new ProductAttribute();
            size.setId(11);
            size.setName("size");
        }

        @Test
        void assignsAttributesInListOrderOnCreate() {
            when(productCategoryRepository.existsByNameIgnoreCase("Clothes")).thenReturn(false);
            when(slugService.generateUniqueSlug(anyString(), any(), any())).thenReturn("clothes");
            when(productAttributeRepository.findAllById(any())).thenReturn(List.of(size, color));
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            List<ProductCategoryService.AttributeAssignmentInput> attributes = List.of(
                    new ProductCategoryService.AttributeAssignmentInput(11, true),
                    new ProductCategoryService.AttributeAssignmentInput(10, false));

            ProductCategory result = service.create("Clothes", null, attributes);

            assertThat(result.getCategoryAttributes()).hasSize(2);
            assertThat(result.getCategoryAttributes().get(0).getAttribute()).isEqualTo(size);
            assertThat(result.getCategoryAttributes().get(0).isRequired()).isTrue();
            assertThat(result.getCategoryAttributes().get(0).getDisplayOrder()).isEqualTo(0);
            assertThat(result.getCategoryAttributes().get(1).getAttribute()).isEqualTo(color);
            assertThat(result.getCategoryAttributes().get(1).isRequired()).isFalse();
            assertThat(result.getCategoryAttributes().get(1).getDisplayOrder()).isEqualTo(1);
        }

        @Test
        void rejectsADuplicateAttributeIdInTheSameRequest() {
            when(productCategoryRepository.existsByNameIgnoreCase("Clothes")).thenReturn(false);
            when(slugService.generateUniqueSlug(anyString(), any(), any())).thenReturn("clothes");
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            List<ProductCategoryService.AttributeAssignmentInput> attributes = List.of(
                    new ProductCategoryService.AttributeAssignmentInput(10, true),
                    new ProductCategoryService.AttributeAssignmentInput(10, false));

            assertThatThrownBy(() -> service.create("Clothes", null, attributes))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_CATEGORY_ATTRIBUTE_DUPLICATE);
        }

        @Test
        void rejectsAnAttributeIdThatDoesNotExist() {
            when(productCategoryRepository.existsByNameIgnoreCase("Clothes")).thenReturn(false);
            when(slugService.generateUniqueSlug(anyString(), any(), any())).thenReturn("clothes");
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(productAttributeRepository.findAllById(any())).thenReturn(List.of());

            List<ProductCategoryService.AttributeAssignmentInput> attributes =
                    List.of(new ProductCategoryService.AttributeAssignmentInput(99, true));

            // ApiException, not ResourceNotFoundException — mirrors ProductServiceImplTest
            // .rejectsUnknownTagId's own identical precedent (a bulk existence-count check via
            // Validator.isTrue, not Validator.notFound's own single-lookup shape).
            assertThatThrownBy(() -> service.create("Clothes", null, attributes))
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getErrorCode())
                    .isEqualTo(EcommerceErrorCode.PRODUCT_ATTRIBUTE_NOT_FOUND);
        }

        @Test
        void leavesExistingAttributesUntouchedWhenUpdateOmitsThem() {
            when(productCategoryRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            service.update(1, "Apparel", null, null);

            verify(productAttributeRepository, never()).findAllById(any());
        }

        @Test
        void clearsExistingAttributesWhenUpdateSendsAnEmptyList() {
            ProductCategoryAttribute assignment = new ProductCategoryAttribute();
            assignment.setCategory(existing);
            assignment.setAttribute(color);
            assignment.setRequired(true);
            assignment.setDisplayOrder(0);
            existing.getCategoryAttributes().add(assignment);
            when(productCategoryRepository.findById(1)).thenReturn(Optional.of(existing));
            when(productCategoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ProductCategory result = service.update(1, "Apparel", null, List.of());

            assertThat(result.getCategoryAttributes()).isEmpty();
        }
    }
}
