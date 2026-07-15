package com.ttg.devknowledgeplatform.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.content.entity.Tag;
import com.ttg.devknowledgeplatform.content.dto.TagResponse;

@Mapper(componentModel = "spring")
public interface TagMapper {

    @Mapping(source = "dteCreation", target = "createdAt")
    @Mapping(source = "dteLastModification", target = "updatedAt")
    TagResponse toResponse(Tag tag);
}
