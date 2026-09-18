package com.ttg.devknowledgeplatform.devpractice.dto;

public record TestCaseResponse(Integer id, String input, String expectedOutput, Boolean sample) {
}
