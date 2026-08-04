package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.spec.ProductCategorySpecification;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCategoryService;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ProductCategoryServiceImpl implements ProductCategoryService {

    private final ProductCategoryRepository productCategoryRepository;
    private final SlugService slugService;

    @Override
    public ProductCategory create(String name) {
        String normalizedName = normalizeName(name);
        if (productCategoryRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new ApiException(EcommerceErrorCode.PRODUCT_CATEGORY_NAME_CONFLICT, new Object[] {normalizedName});
        }
        String slug = slugService.generateUniqueSlug(
                normalizedName, productCategoryRepository::existsBySlug, EcommerceErrorCode.PRODUCT_CATEGORY_SLUG_CONFLICT);

        ProductCategory category = new ProductCategory();
        category.setName(normalizedName);
        category.setSlug(slug);

        ProductCategory saved = productCategoryRepository.save(category);
        log.info("Created product category id={} slug={}", saved.getId(), slug);
        return saved;
    }

    @Override
    public ProductCategory update(Integer id, String name) {
        ProductCategory category = findById(id);
        String normalizedName = normalizeName(name);

        if (!category.getName().equalsIgnoreCase(normalizedName)) {
            if (productCategoryRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
                throw new ApiException(EcommerceErrorCode.PRODUCT_CATEGORY_NAME_CONFLICT, new Object[] {normalizedName});
            }
            category.setName(normalizedName);
            category.setSlug(slugService.generateUniqueSlug(
                    normalizedName, productCategoryRepository::existsBySlugAndIdNot, id,
                    EcommerceErrorCode.PRODUCT_CATEGORY_SLUG_CONFLICT));
        }

        ProductCategory updated = productCategoryRepository.save(category);
        log.info("Updated product category id={}", id);
        return updated;
    }

    @Override
    public ProductCategory getById(Integer id) {
        return findById(id);
    }

    @Override
    public List<ProductCategory> list(String q) {
        Specification<ProductCategory> spec = ProductCategorySpecification.withFilters(q);
        return productCategoryRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "name"));
    }

    private ProductCategory findById(Integer id) {
        return productCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        EcommerceErrorCode.PRODUCT_CATEGORY_NOT_FOUND, new Object[] {id}));
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }
}
