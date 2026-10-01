package com.ttg.devknowledgeplatform.devpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Data;

/**
 * Create/update payload for a problem tag — one class for both, since a tag has a single editable
 * field (its slug is always regenerated from the name).
 */
@Data
public class ProblemTagRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;
}
