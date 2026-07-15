package com.ttg.devknowledgeplatform.content.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ttg.devknowledgeplatform.content.enums.ContentStatus;
import com.ttg.devknowledgeplatform.content.enums.QuestionDifficulty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionAnswerResponse {

    private Integer id;
    private Integer contentItemId;
    private String title;
    private String slug;
    private QuestionDifficulty difficulty;
    private String questionBody;
    private String shortAnswer;
    private String detailedAnswer;
    private Boolean isCommon;
    private ContentStatus status;
    private Integer categoryId;
    private Set<Integer> tagIds;
    private Instant createdAt;
    private Instant updatedAt;
}
