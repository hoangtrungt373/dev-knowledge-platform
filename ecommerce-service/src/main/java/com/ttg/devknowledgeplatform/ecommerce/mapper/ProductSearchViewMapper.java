package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.ProductSearchResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductSearchView;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductSearchViewMapper {

    // product.id is available off the (possibly-uninitialized) @OneToOne proxy without a DB
    // round trip — Hibernate resolves an entity proxy's identifier from its own FK column.
    @Mapping(source = "product.id", target = "productId")
    ProductSearchResponse toResponse(ProductSearchView view);
}
