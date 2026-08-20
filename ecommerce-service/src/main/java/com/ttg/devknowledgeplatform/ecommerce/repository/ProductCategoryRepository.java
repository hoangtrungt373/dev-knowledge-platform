package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductCategoryRepository
        extends JpaRepository<ProductCategory, Integer>, JpaSpecificationExecutor<ProductCategory> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Integer id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);

    /** Used by {@code service.seed.ProductCategorySeeder}/{@code ProductSeeder} to resolve a seed row's category by name. */
    Optional<ProductCategory> findByNameIgnoreCase(String name);
}
