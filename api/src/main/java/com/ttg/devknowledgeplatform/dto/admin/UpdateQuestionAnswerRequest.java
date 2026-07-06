package com.ttg.devknowledgeplatform.dto.admin;

import com.ttg.devknowledgeplatform.content.enums.ContentStatus;
import com.ttg.devknowledgeplatform.content.enums.QuestionDifficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateQuestionAnswerRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must not exceed 500 characters")
    private String title;

    /** Optional — interview-specific metadata; leave null for general knowledge content. */
    private QuestionDifficulty difficulty;

    @NotBlank(message = "Question body is required")
    private String questionBody;

    private String shortAnswer;

    private String detailedAnswer;

    private Boolean isCommon;

    private ContentStatus status;

    private Integer categoryId;

    /** Null = leave tags unchanged; empty = clear all; otherwise replace (deduped in service). */
    private Set<Integer> tagIds;
}
