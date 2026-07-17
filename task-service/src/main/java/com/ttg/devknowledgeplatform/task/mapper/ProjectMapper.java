package com.ttg.devknowledgeplatform.task.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.task.dto.ProjectResponse;
import com.ttg.devknowledgeplatform.task.entity.Project;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    @Mapping(target = "createdAt", source = "dteCreation")
    ProjectResponse toResponse(Project project);
}
