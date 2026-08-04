package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.ProductImageResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductResponse;
import com.ttg.devknowledgeplatform.ecommerce.dto.ProductVariantResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.Product;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductImage;
import com.ttg.devknowledgeplatform.ecommerce.entity.ProductVariant;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(source = "productCategory.id", target = "productCategoryId")
    @Mapping(source = "productCategory.name", target = "categoryName")
    @Mapping(source = "dteCreation", target = "createdAt")
    @Mapping(source = "dteLastModification", target = "updatedAt")
    ProductResponse toResponse(Product product);

    ProductVariantResponse toVariantResponse(ProductVariant variant);

    ProductImageResponse toImageResponse(ProductImage image);
}
