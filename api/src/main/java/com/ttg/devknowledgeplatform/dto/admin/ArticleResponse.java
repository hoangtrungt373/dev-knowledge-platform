package com.ttg.devknowledgeplatform.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArticleResponse {

    private Integer id;
    private Integer contentItemId;
    private String title;
    private String slug;
    private ContentType type;
    private String body;
    private ContentStatus status;
    private Integer categoryId;
    private Set<Integer> tagIds;
    private Integer viewCount;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
