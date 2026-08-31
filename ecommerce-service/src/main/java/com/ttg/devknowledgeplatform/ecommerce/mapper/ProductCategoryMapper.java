package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.ProductCategoryResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductCategoryTreeNodeResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductCategory;
import com.ttg.devknowledgeplatform.ecommerce.service.ProductCategoryTreeNode;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductCategoryMapper {

    @Mapping(source = "dteCreation", target = "createdAt")
    @Mapping(source = "dteLastModification", target = "updatedAt")
    @Mapping(source = "parent.id", target = "parentId")
    ProductCategoryResponse toResponse(ProductCategory category);

    @Mapping(source = "category.id", target = "id")
    @Mapping(source = "category.name", target = "name")
    @Mapping(source = "category.slug", target = "slug")
    @Mapping(source = "category.parent.id", target = "parentId")
    @Mapping(source = "children", target = "children")
    ProductCategoryTreeNodeResponse toTreeNodeResponse(ProductCategoryTreeNode node);
}
