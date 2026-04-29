package com.ttg.devknowledgeplatform.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ttg.devknowledgeplatform.common.enums.TagStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagResponse {

    private Integer id;
    private String name;
    private String slug;
    private TagStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
