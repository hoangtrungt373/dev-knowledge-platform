package com.ttg.devknowledgeplatform.dto.admin;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.QuestionDifficulty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class CreateQuestionAnswerRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must not exceed 500 characters")
    private String title;

    /** Optional — interview-specific metadata; leave null for general knowledge content. */
    private QuestionDifficulty difficulty;

    @NotBlank(message = "Question body is required")
    private String questionBody;

    private String shortAnswer;

    private String detailedAnswer;

    /** Optional — interview-specific metadata; leave null for general knowledge content. */
    private Boolean isCommon;

    private ContentStatus status = ContentStatus.DRAFT;

    private Integer categoryId;

    /** Deduped in service; null or empty means no tags. */
    private Set<Integer> tagIds;
}
