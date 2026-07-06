package com.ttg.devknowledgeplatform.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.content.entity.Category;
import com.ttg.devknowledgeplatform.content.service.CategoryTreeNode;
import com.ttg.devknowledgeplatform.dto.content.CategoryResponse;
import com.ttg.devknowledgeplatform.dto.content.CategoryTreeNodeResponse;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    @Mapping(source = "parent.id", target = "parentId")
    @Mapping(source = "dteCreation", target = "createdAt")
    @Mapping(source = "dteLastModification", target = "updatedAt")
    CategoryResponse toResponse(Category category);

    @Mapping(source = "category.id", target = "id")
    @Mapping(source = "category.name", target = "name")
    @Mapping(source = "category.slug", target = "slug")
    @Mapping(source = "category.parent.id", target = "parentId")
    @Mapping(source = "children", target = "children")
    CategoryTreeNodeResponse toTreeNodeResponse(CategoryTreeNode node);
}
