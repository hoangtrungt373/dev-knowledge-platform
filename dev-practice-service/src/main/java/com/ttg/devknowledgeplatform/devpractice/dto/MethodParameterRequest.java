package com.ttg.devknowledgeplatform.devpractice.dto;

import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.harness.SignatureNameValidator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import lombok.Data;

/** One parameter of {@link CreateProblemRequest}/{@link UpdateProblemRequest}'s method signature. */
@Data
public class MethodParameterRequest {

    @NotBlank(message = "Parameter name is required")
    @Pattern(regexp = SignatureNameValidator.IDENTIFIER_REGEX,
            message = "Parameter name must use ASCII letters, digits and _, starting with a letter")
    private String name;

    @NotNull(message = "Parameter type is required")
    private ParamType type;
}
