package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.ProductAttributeResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductAttributeValueResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttribute;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductAttributeValue;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductAttributeMapper {

    @Mapping(source = "dteCreation", target = "createdAt")
    @Mapping(source = "dteLastModification", target = "updatedAt")
    ProductAttributeResponse toResponse(ProductAttribute attribute);

    ProductAttributeValueResponse toValueResponse(ProductAttributeValue value);
}
