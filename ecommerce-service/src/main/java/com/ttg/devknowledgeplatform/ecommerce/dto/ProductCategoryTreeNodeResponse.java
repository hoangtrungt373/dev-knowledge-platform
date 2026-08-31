package com.ttg.devknowledgeplatform.ecommerce.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** REST response shape for one node of {@code ProductCategory}'s hierarchy — see {@code ProductCategoryApi#tree}. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductCategoryTreeNodeResponse {

    private Integer id;
    private String name;
    private String slug;
    /** Null for a root category. */
    private Integer parentId;

    @Builder.Default
    private List<ProductCategoryTreeNodeResponse> children = new ArrayList<>();
}
