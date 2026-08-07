package com.ttg.devknowledgeplatform.ai.dto.client;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.enums.QuestionDifficulty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Deserialized shape of a single element from {@code content-service}'s
 * {@code InternalContentItemResponse} JSON — this module's own copy of that contract rather than a
 * shared class, same "duplicate, don't share cross-service DTOs" convention every
 * {@code KeycloakJwtAuthenticationConverter} duplicate in this codebase already follows. Keep field
 * names in sync with {@code content-service}'s {@code InternalContentItemResponse} by hand; there is
 * no compiler to catch drift once these two classes live in separate deployables.
 */
@Data
public class ContentItemDto {

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

    private String questionBody;
    private String shortAnswer;
    private String detailedAnswer;
    private QuestionDifficulty difficulty;
    private Boolean isCommon;

    private String body;
}
