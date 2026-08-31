package com.ttg.devknowledgeplatform.ecommerce.dto;

import jakarta.validation.constraints.NotEmpty;

import lombok.Data;

import java.util.List;

/** Request payload to remove multiple lines from the cart in one call (bulk delete). */
@Data
public class RemoveCartItemsRequest {

    @NotEmpty(message = "At least one variant id is required")
    private List<Integer> variantIds;
}
