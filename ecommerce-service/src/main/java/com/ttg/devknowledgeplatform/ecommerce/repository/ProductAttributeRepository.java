package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttribute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Integer>, JpaSpecificationExecutor<ProductAttribute> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Integer id);
}
