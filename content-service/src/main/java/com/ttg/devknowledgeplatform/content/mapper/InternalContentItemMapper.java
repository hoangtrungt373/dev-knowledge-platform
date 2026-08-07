package com.ttg.devknowledgeplatform.content.mapper;

import com.ttg.devknowledgeplatform.content.dto.internal.InternalContentItemResponse;
import com.ttg.devknowledgeplatform.content.entity.Article;
import com.ttg.devknowledgeplatform.content.entity.ContentItem;
import com.ttg.devknowledgeplatform.content.entity.ContentItemTag;
import com.ttg.devknowledgeplatform.content.entity.QuestionAnswer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Maps a {@code ContentItem} plus its optional {@code QuestionAnswer}/{@code Article} subtype row
 * to the flattened DTO {@code ai-service} consumes over the internal indexing API.
 *
 * <p>{@code qa}/{@code article} are mutually exclusive and both nullable — MapStruct generates a
 * null guard around each parameter before dereferencing its fields, so passing {@code null} for
 * whichever subtype doesn't apply to a given {@code ContentItem.type} simply leaves those target
 * fields unset.
 */
@Mapper(componentModel = "spring")
public interface InternalContentItemMapper {

    @Mapping(source = "item.id", target = "id")
    @Mapping(source = "item.type", target = "type")
    @Mapping(source = "item.status", target = "status")
    @Mapping(source = "item.title", target = "title")
    @Mapping(source = "item.slug", target = "slug")
    @Mapping(source = "item.qualityScore", target = "qualityScore")
    @Mapping(source = "item.category.id", target = "categoryId")
    @Mapping(source = "item.category.name", target = "categoryName")
    @Mapping(source = "item.contentItemTags", target = "tagIds")
    @Mapping(source = "item.contentItemTags", target = "tagNames")
    @Mapping(source = "qa.questionBody", target = "questionBody")
    @Mapping(source = "qa.shortAnswer", target = "shortAnswer")
    @Mapping(source = "qa.detailedAnswer", target = "detailedAnswer")
    @Mapping(source = "qa.difficulty", target = "difficulty")
    @Mapping(source = "qa.isCommon", target = "isCommon")
    @Mapping(source = "article.body", target = "body")
    InternalContentItemResponse toResponse(ContentItem item, QuestionAnswer qa, Article article);

    default List<Integer> toTagIds(Set<ContentItemTag> tags) {
        if (tags == null || tags.isEmpty()) return List.of();
        List<Integer> ids = new ArrayList<>();
        for (ContentItemTag cit : tags) {
            ids.add(cit.getTag().getId());
        }
        return ids;
    }

    default List<String> toTagNames(Set<ContentItemTag> tags) {
        if (tags == null || tags.isEmpty()) return List.of();
        List<String> names = new ArrayList<>();
        for (ContentItemTag cit : tags) {
            names.add(cit.getTag().getName());
        }
        return names;
    }
}
