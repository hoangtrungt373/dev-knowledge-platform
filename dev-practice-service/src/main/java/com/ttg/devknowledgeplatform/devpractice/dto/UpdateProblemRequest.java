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

/** {@code testCases}/{@code parameters} are both replace-all — the supplied list becomes the problem's full set. */
@Data
public class UpdateProblemRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Difficulty is required")
    private Difficulty difficulty;

    /** Optional — a null value leaves the current status unchanged. */
    private ContentStatus status;

    @NotBlank(message = "Method name is required")
    private String methodName;

    @NotNull(message = "Return type is required")
    private ParamType returnType;

    @NotEmpty(message = "At least one parameter is required")
    @Valid
    private List<MethodParameterRequest> parameters;

    @NotEmpty(message = "At least one test case is required")
    @Valid
    private List<TestCaseRequest> testCases;
}
