package com.ttg.devknowledgeplatform.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryResponse {

    private Integer id;
    private String name;
    private String slug;
    private Integer parentId;
    private Instant createdAt;
    private Instant updatedAt;
}
