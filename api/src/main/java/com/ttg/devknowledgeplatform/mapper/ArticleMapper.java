package com.ttg.devknowledgeplatform.mapper;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.common.entity.Article;
import com.ttg.devknowledgeplatform.common.entity.ContentItemTag;
import com.ttg.devknowledgeplatform.dto.admin.ArticleResponse;

@Mapper(componentModel = "spring")
public interface ArticleMapper {

    @Mapping(source = "contentItem.id", target = "contentItemId")
    @Mapping(source = "contentItem.title", target = "title")
    @Mapping(source = "contentItem.slug", target = "slug")
    @Mapping(source = "contentItem.type", target = "type")
    @Mapping(source = "contentItem.status", target = "status")
    @Mapping(source = "contentItem.category.id", target = "categoryId")
    @Mapping(source = "contentItem.contentItemTags", target = "tagIds")
    @Mapping(source = "contentItem.viewCount", target = "viewCount")
    @Mapping(source = "contentItem.publishedAt", target = "publishedAt")
    @Mapping(source = "dteCreation", target = "createdAt")
    @Mapping(source = "dteLastModification", target = "updatedAt")
    ArticleResponse toResponse(Article article);

    default Set<Integer> toTagIds(Set<ContentItemTag> tags) {
        if (tags == null || tags.isEmpty()) return Set.of();
        return tags.stream()
                .map(cit -> cit.getTag().getId())
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
