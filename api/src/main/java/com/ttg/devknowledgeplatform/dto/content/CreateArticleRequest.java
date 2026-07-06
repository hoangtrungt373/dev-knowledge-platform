package com.ttg.devknowledgeplatform.dto.content;

import com.ttg.devknowledgeplatform.content.enums.ContentStatus;
import com.ttg.devknowledgeplatform.content.enums.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

@Data
public class CreateArticleRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 500, message = "Title must not exceed 500 characters")
    private String title;

    /** Must be ARTICLE or BLOG_POST. */
    @NotNull(message = "Type is required")
    private ContentType type;

    private String body;

    private ContentStatus status = ContentStatus.DRAFT;

    private Integer categoryId;

    private Set<Integer> tagIds;
}
