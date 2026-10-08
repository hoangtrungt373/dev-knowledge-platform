package com.ttg.devknowledgeplatform.devpractice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.devpractice.dto.ProblemProgressResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.RunResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.SubmissionResponse;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemProgress;
import com.ttg.devknowledgeplatform.devpractice.service.RunResult;

@Mapper(componentModel = "spring")
public interface SubmissionMapper {

    @Mapping(target = "problemId", source = "problem.id")
    @Mapping(target = "problemTitle", source = "problem.title")
    @Mapping(target = "submittedAt", source = "dteCreation")
    SubmissionResponse toResponse(Submission submission);

    ProblemProgressResponse toResponse(ProblemProgress progress);

    RunResponse toResponse(RunResult result);

    RunResponse.CaseResponse toResponse(RunResult.Case runCase);
}
