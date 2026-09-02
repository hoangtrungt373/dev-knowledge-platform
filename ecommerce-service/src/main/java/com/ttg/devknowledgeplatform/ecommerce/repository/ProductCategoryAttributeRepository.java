package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategoryAttribute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Read-only outside of {@code ProductCategory.categoryAttributes}' own cascade — the only write
 * paths for this entity are via that collection (see its Javadoc), never a direct {@code save}/
 * {@code delete} through this repository. {@link #existsByAttributeId} backs
 * {@code ProductAttributeServiceImpl.delete}'s in-use guard, mirroring
 * {@code ProductTagAssignmentRepository.existsByProductTagId}.
 */
@Repository
public interface ProductCategoryAttributeRepository extends JpaRepository<ProductCategoryAttribute, Integer> {

    boolean existsByAttributeId(Integer attributeId);
}
