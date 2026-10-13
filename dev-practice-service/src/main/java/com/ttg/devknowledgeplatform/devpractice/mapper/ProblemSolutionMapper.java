package com.ttg.devknowledgeplatform.devpractice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSolutionResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.SolutionCodeResponse;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemSolution;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;

/**
 * Editorial approach → learner DTO. {@code codes} always goes through
 * {@link ProblemSolution#verifiedCode()} — never the raw links — so a pending, rejected or stale run's
 * code can't reach a learner.
 */
@Mapper(componentModel = "spring")
public interface ProblemSolutionMapper {

    @Mapping(target = "codes", expression = "java(solution.verifiedCode().stream().map(this::toCodeResponse).toList())")
    ProblemSolutionResponse toResponse(ProblemSolution solution);

    SolutionCodeResponse toCodeResponse(Submission run);
}
