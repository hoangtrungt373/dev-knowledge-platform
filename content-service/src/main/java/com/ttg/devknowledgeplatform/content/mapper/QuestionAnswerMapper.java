package com.ttg.devknowledgeplatform.content.mapper;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.content.entity.ContentItemTag;
import com.ttg.devknowledgeplatform.content.entity.QuestionAnswer;
import com.ttg.devknowledgeplatform.content.dto.QuestionAnswerResponse;

@Mapper(componentModel = "spring")
public interface QuestionAnswerMapper {

    @Mapping(source = "contentItem.id", target = "contentItemId")
    @Mapping(source = "contentItem.title", target = "title")
    @Mapping(source = "contentItem.slug", target = "slug")
    @Mapping(source = "contentItem.status", target = "status")
    @Mapping(source = "contentItem.category.id", target = "categoryId")
    @Mapping(source = "contentItem.contentItemTags", target = "tagIds")
    @Mapping(source = "dteCreation", target = "createdAt")
    @Mapping(source = "dteLastModification", target = "updatedAt")
    QuestionAnswerResponse toResponse(QuestionAnswer question);

    default Set<Integer> toTagIds(Set<ContentItemTag> tags) {
        if (tags == null || tags.isEmpty()) return Set.of();
        return tags.stream()
                .map(cit -> cit.getTag().getId())
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
