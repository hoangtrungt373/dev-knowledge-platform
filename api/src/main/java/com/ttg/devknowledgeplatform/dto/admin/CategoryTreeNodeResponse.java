package com.ttg.devknowledgeplatform.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CategoryTreeNodeResponse {

    private Integer id;
    private String name;
    private String slug;
    private Integer parentId;

    @Builder.Default
    private List<CategoryTreeNodeResponse> children = new ArrayList<>();
}
