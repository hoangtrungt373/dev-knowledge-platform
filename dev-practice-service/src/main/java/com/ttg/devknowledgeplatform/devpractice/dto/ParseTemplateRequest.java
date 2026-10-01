package com.ttg.devknowledgeplatform.devpractice.dto;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Data;

/** A code template to read a method signature out of — see {@code harness.SignatureTemplateParser}. */
@Data
public class ParseTemplateRequest {

    @NotNull(message = "Language is required")
    private ProgrammingLanguage language;

    @NotBlank(message = "Code template is required")
    @Size(max = 20000, message = "Code template must not exceed 20000 characters")
    private String code;
}
