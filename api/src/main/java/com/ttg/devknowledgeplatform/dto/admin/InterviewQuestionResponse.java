package com.ttg.devknowledgeplatform.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.InterviewQuestionDifficulty;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InterviewQuestionResponse {

    private Integer id;
    private Integer contentItemId;
    private String title;
    private String slug;
    private InterviewQuestionDifficulty difficulty;
    private String questionBody;
    private String shortAnswer;
    private String detailedAnswer;
    private Boolean isCommon;
    private ContentStatus status;
    private Integer categoryId;
    private Instant createdAt;
    private Instant updatedAt;
}