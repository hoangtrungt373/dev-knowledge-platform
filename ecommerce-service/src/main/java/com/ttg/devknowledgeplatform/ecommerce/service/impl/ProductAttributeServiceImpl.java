package com.ttg.devknowledgeplatform.ecommerce.service.impl;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttribute;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttributeValue;
import com.ttg.devknowledgeplatform.ecommerce.exception.EcommerceErrorCode;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductAttributeRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.ProductCategoryAttributeRepository;
import com.ttg.devknowledgeplatform.ecommerce.repository.spec.ProductAttributeSpecification;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductAttributeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ProductAttributeServiceImpl implements ProductAttributeService {

    private final ProductAttributeRepository productAttributeRepository;
    private final ProductCategoryAttributeRepository productCategoryAttributeRepository;

    @Override
    public ProductAttribute create(String name, List<String> values) {
        String normalizedName = normalizeName(name);
        Validator.isFalse(productAttributeRepository.existsByNameIgnoreCase(normalizedName),
                EcommerceErrorCode.PRODUCT_ATTRIBUTE_NAME_CONFLICT, normalizedName);

        ProductAttribute attribute = new ProductAttribute();
        attribute.setName(normalizedName);
        ProductAttribute saved = productAttributeRepository.save(attribute);
        applyValues(saved, values);

        log.info("Created product attribute id={} name={} valueCount={}", saved.getId(), normalizedName, saved.getValues().size());
        return saved;
    }

    @Override
    public ProductAttribute update(Integer id, String name, List<String> values) {
        ProductAttribute attribute = findById(id);
        String normalizedName = normalizeName(name);

        if (!attribute.getName().equalsIgnoreCase(normalizedName)) {
            Validator.isFalse(productAttributeRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id),
                    EcommerceErrorCode.PRODUCT_ATTRIBUTE_NAME_CONFLICT, normalizedName);
            attribute.setName(normalizedName);
        }
        applyValues(attribute, values);

        ProductAttribute updated = productAttributeRepository.save(attribute);
        log.info("Updated product attribute id={} valueCount={}", id, updated.getValues().size());
        return updated;
    }

    @Override
    public ProductAttribute getById(Integer id) {
        return findById(id);
    }

    @Override
    public Page<ProductAttribute> list(Pageable pageable, String q) {
        Specification<ProductAttribute> spec = ProductAttributeSpecification.withFilters(q);
        return productAttributeRepository.findAll(spec, pageable);
    }

    @Override
    public void delete(Integer id) {
        ProductAttribute attribute = findById(id);
        Validator.isFalse(productCategoryAttributeRepository.existsByAttributeId(id),
                EcommerceErrorCode.PRODUCT_ATTRIBUTE_IN_USE, id);
        productAttributeRepository.delete(attribute);
        log.info("Deleted product attribute id={}", id);
    }

    private ProductAttribute findById(Integer id) {
        return Validator.notFound(productAttributeRepository.findById(id), EcommerceErrorCode.PRODUCT_ATTRIBUTE_NOT_FOUND, id);
    }

    /**
     * Clears and rebuilds {@code attribute.values} from {@code values}, in list order (list
     * position becomes {@link ProductAttributeValue#getDisplayOrder()} — there is no independent,
     * caller-supplied order number). Rejects an empty list (US-1.6-style "must have at least one"
     * rule, mirroring {@code Product}'s own "at least one variant" requirement) and a duplicate
     * value (case-insensitive).
     */
    private static void applyValues(ProductAttribute attribute, List<String> values) {
        List<String> trimmed = values == null ? List.of() : values.stream().map(String::trim).toList();
        Validator.isFalse(trimmed.isEmpty(), EcommerceErrorCode.PRODUCT_ATTRIBUTE_VALUES_REQUIRED);

        Set<String> seenLower = new LinkedHashSet<>();
        for (String value : trimmed) {
            Validator.isTrue(seenLower.add(value.toLowerCase(Locale.ROOT)),
                    EcommerceErrorCode.PRODUCT_ATTRIBUTE_VALUE_DUPLICATE, value);
        }

        attribute.getValues().clear();
        for (int i = 0; i < trimmed.size(); i++) {
            ProductAttributeValue attributeValue = new ProductAttributeValue();
            attributeValue.setAttribute(attribute);
            attributeValue.setValue(trimmed.get(i));
            attributeValue.setDisplayOrder(i);
            attribute.getValues().add(attributeValue);
        }
    }

    private static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }
}
