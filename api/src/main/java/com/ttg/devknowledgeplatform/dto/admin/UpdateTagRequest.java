package com.ttg.devknowledgeplatform.dto.admin;

import com.ttg.devknowledgeplatform.common.enums.TagStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateTagRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    private String name;

    private TagStatus status;
}
