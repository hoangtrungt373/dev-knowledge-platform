package com.ttg.devknowledgeplatform.devpractice.dto;

import java.util.List;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

@Data
public class CreateProblemRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Difficulty is required")
    private Difficulty difficulty;

    /** Optional — defaults to {@code DRAFT} in the service layer if not supplied. */
    private ContentStatus status;

    @NotBlank(message = "Method name is required")
    private String methodName;

    @NotNull(message = "Return type is required")
    private ParamType returnType;

    /** Method signature parameters, in order — a parameter's position is its index in this list. */
    @NotEmpty(message = "At least one parameter is required")
    @Valid
    private List<MethodParameterRequest> parameters;

    @NotEmpty(message = "At least one test case is required")
    @Valid
    private List<TestCaseRequest> testCases;
}
