package com.ttg.devknowledgeplatform.mapper;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.common.entity.ContentItemTag;
import com.ttg.devknowledgeplatform.common.entity.InterviewQuestion;
import com.ttg.devknowledgeplatform.dto.admin.InterviewQuestionResponse;

@Mapper(componentModel = "spring")
public interface InterviewQuestionMapper {

    @Mapping(source = "contentItem.id", target = "contentItemId")
    @Mapping(source = "contentItem.title", target = "title")
    @Mapping(source = "contentItem.slug", target = "slug")
    @Mapping(source = "contentItem.status", target = "status")
    @Mapping(source = "contentItem.category.id", target = "categoryId")
    @Mapping(source = "contentItem.contentItemTags", target = "tagIds")
    @Mapping(source = "dteCreation", target = "createdAt")
    @Mapping(source = "dteLastModification", target = "updatedAt")
    InterviewQuestionResponse toResponse(InterviewQuestion question);

    default Set<Integer> toTagIds(Set<ContentItemTag> tags) {
        if (tags == null || tags.isEmpty()) return Set.of();
        return tags.stream()
                .map(cit -> cit.getTag().getId())
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
