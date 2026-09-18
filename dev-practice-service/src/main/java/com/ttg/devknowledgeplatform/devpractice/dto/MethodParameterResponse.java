package com.ttg.devknowledgeplatform.devpractice.dto;

import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

public record MethodParameterResponse(String name, ParamType type, Integer position) {
}
