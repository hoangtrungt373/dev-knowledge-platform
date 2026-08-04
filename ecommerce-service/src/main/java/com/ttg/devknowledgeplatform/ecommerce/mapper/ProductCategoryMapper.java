package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.ProductCategoryResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductCategoryMapper {

    @Mapping(source = "dteCreation", target = "createdAt")
    @Mapping(source = "dteLastModification", target = "updatedAt")
    ProductCategoryResponse toResponse(ProductCategory category);
}
