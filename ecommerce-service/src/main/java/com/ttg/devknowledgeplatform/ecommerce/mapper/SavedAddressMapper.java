package com.ttg.devknowledgeplatform.ecommerce.mapper;

import com.ttg.devknowledgeplatform.ecommerce.dto.SavedAddressResponse;
import com.ttg.devknowledgeplatform.ecommerce.entity.SavedAddress;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SavedAddressMapper {

    @Mapping(source = "dteCreation", target = "createdAt")
    @Mapping(source = "dteLastModification", target = "updatedAt")
    SavedAddressResponse toResponse(SavedAddress address);
}
