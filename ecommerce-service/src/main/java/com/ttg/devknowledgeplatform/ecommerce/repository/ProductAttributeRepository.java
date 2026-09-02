package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttribute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Integer>, JpaSpecificationExecutor<ProductAttribute> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    /** Used by {@code service.seed.ProductCategoryAttributeSeeder} to resolve a seed row's
     * attribute by name. */
    Optional<ProductAttribute> findByNameIgnoreCase(String name);
}
