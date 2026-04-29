package com.ttg.devknowledgeplatform.dto.admin;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.InterviewQuestionDifficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateInterviewQuestionRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must not exceed 500 characters")
    private String title;

    @NotNull(message = "Difficulty is required")
    private InterviewQuestionDifficulty difficulty;

    @NotBlank(message = "Question body is required")
    private String questionBody;

    private String shortAnswer;

    private String detailedAnswer;

    private Boolean isCommon;

    private ContentStatus status;

    private Integer categoryId;
}