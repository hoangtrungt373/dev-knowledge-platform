package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductTagAssignment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Read-only outside of {@code Product.productTagAssignments}' own cascade — the only write paths
 * for this entity are via that collection (see its Javadoc), never a direct {@code save}/
 * {@code delete} through this repository. {@link #existsByProductTagId} backs
 * {@code ProductTagServiceImpl.delete}'s in-use guard, mirroring
 * {@code content-service}'s {@code ContentItemTagRepository.existsByTagId}.
 */
@Repository
public interface ProductTagAssignmentRepository extends JpaRepository<ProductTagAssignment, Integer> {

    boolean existsByProductTagId(Integer productTagId);
}
