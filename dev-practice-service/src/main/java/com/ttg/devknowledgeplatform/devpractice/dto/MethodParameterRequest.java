package com.ttg.devknowledgeplatform.devpractice.dto;

import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Data;

/** One parameter of {@link CreateProblemRequest}/{@link UpdateProblemRequest}'s method signature. */
@Data
public class MethodParameterRequest {

    @NotBlank(message = "Parameter name is required")
    private String name;

    @NotNull(message = "Parameter type is required")
    private ParamType type;
}
