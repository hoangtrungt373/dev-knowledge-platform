package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductTag;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductTagAssignmentRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductTagRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.spec.ProductTagSpecification;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductTagService;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ProductTagServiceImpl implements ProductTagService {

    private final ProductTagRepository productTagRepository;
    private final ProductTagAssignmentRepository productTagAssignmentRepository;
    private final SlugService slugService;

    @Override
    public ProductTag create(String name) {
        String normalizedName = normalizeName(name);
        Validator.isFalse(productTagRepository.existsByNameIgnoreCase(normalizedName),
                EcommerceErrorCode.PRODUCT_TAG_NAME_CONFLICT, normalizedName);
        String slug = slugService.generateUniqueSlug(
                normalizedName, productTagRepository::existsBySlug, EcommerceErrorCode.PRODUCT_TAG_SLUG_CONFLICT);

        ProductTag tag = new ProductTag();
        tag.setName(normalizedName);
        tag.setSlug(slug);

        ProductTag saved = productTagRepository.save(tag);
        log.info("Created product tag id={} slug={}", saved.getId(), slug);
        return saved;
    }

    @Override
    public ProductTag update(Integer id, String name) {
        ProductTag tag = findById(id);
        String normalizedName = normalizeName(name);

        if (!tag.getName().equalsIgnoreCase(normalizedName)) {
            Validator.isFalse(productTagRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id),
                    EcommerceErrorCode.PRODUCT_TAG_NAME_CONFLICT, normalizedName);
            tag.setName(normalizedName);
            tag.setSlug(slugService.generateUniqueSlug(
                    normalizedName, productTagRepository::existsBySlugAndIdNot, id,
                    EcommerceErrorCode.PRODUCT_TAG_SLUG_CONFLICT));
        }

        ProductTag updated = productTagRepository.save(tag);
        log.info("Updated product tag id={}", id);
        return updated;
    }

    @Override
    public ProductTag getById(Integer id) {
        return findById(id);
    }

    @Override
    public Page<ProductTag> list(Pageable pageable, String q) {
        Specification<ProductTag> spec = ProductTagSpecification.withFilters(q);
        return productTagRepository.findAll(spec, pageable);
    }

    @Override
    public void delete(Integer id) {
        ProductTag tag = findById(id);
        Validator.isFalse(productTagAssignmentRepository.existsByProductTagId(id), EcommerceErrorCode.PRODUCT_TAG_IN_USE, id);
        productTagRepository.delete(tag);
        log.info("Deleted product tag id={}", id);
    }

    private ProductTag findById(Integer id) {
        return Validator.notFound(productTagRepository.findById(id), EcommerceErrorCode.PRODUCT_TAG_NOT_FOUND, id);
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }
}
