package com.ttg.devknowledgeplatform.task.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.task.dto.TaskResponse;
import com.ttg.devknowledgeplatform.task.entity.Task;

@Mapper(componentModel = "spring")
public interface TaskMapper {

    @Mapping(target = "projectId", expression = "java(task.getProject() != null ? task.getProject().getId() : null)")
    @Mapping(target = "contentItemId", expression = "java(task.getContentItem() != null ? task.getContentItem().getId() : null)")
    @Mapping(target = "createdAt", source = "dteCreation")
    TaskResponse toResponse(Task task);
}
