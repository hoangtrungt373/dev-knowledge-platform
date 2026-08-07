package com.ttg.devknowledgeplatform.content.dto.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.enums.QuestionDifficulty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Server-to-server projection of a {@code ContentItem} (plus its {@code QuestionAnswer}/
 * {@code Article} subtype row, when present) for {@code ai-service}'s indexing pipeline.
 *
 * <p>Unlike {@code ArticleResponse}/{@code QuestionAnswerResponse} (this module's own public/admin
 * REST contracts, which carry only {@code categoryId}/{@code tagIds}), this DTO also carries
 * {@code categoryName}/{@code tagNames} — {@code ai-service}'s {@code ContentEmbeddingMetadata}
 * stores the human-readable names alongside the ids, and once {@code ai-service} can no longer
 * dereference {@code Category}/{@code Tag} itself over a live JPA association, this response is its
 * only source for them. {@code questionBody}/{@code shortAnswer}/{@code detailedAnswer}/
 * {@code difficulty}/{@code isCommon} are populated only when the underlying item is a
 * {@code QUESTION_ANSWER}; {@code body} only when it is an {@code ARTICLE}/{@code BLOG_POST}.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InternalContentItemResponse {

    private Integer id;
    private ContentType type;
    private ContentStatus status;
    private String title;
    private String slug;
    private BigDecimal qualityScore;
    private Integer categoryId;
    private String categoryName;
    private List<Integer> tagIds;
    private List<String> tagNames;

    // Populated only for QUESTION_ANSWER items
    private String questionBody;
    private String shortAnswer;
    private String detailedAnswer;
    private QuestionDifficulty difficulty;
    private Boolean isCommon;

    // Populated only for ARTICLE/BLOG_POST items
    private String body;
}
