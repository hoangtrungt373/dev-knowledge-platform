package com.ttg.devknowledgeplatform.content.dto;

import com.ttg.devknowledgeplatform.content.enums.ContentStatus;
import com.ttg.devknowledgeplatform.content.enums.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateArticleRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must not exceed 500 characters")
    private String title;

    /** Must be ARTICLE or BLOG_POST. */
    private ContentType type;

    private String body;

    private ContentStatus status;

    private Integer categoryId;

    /** Null = leave tags unchanged. Empty set = remove all tags. */
    private Set<Integer> tagIds;
}
