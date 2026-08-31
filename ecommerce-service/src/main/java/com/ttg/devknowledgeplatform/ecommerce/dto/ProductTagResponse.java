package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/** REST response shape for {@code ProductTag}. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductTagResponse {

    private Integer id;
    private String name;
    private String slug;
    private Instant createdAt;
    private Instant updatedAt;
}
