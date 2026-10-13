package com.ttg.devknowledgeplatform.devpractice.api.impl;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.devpractice.api.ProblemSolutionApi;
import com.ttg.devknowledgeplatform.devpractice.dto.AdminProblemSolutionResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSolutionOrderRequest;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSolutionRequest;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemSolution;
import com.ttg.devknowledgeplatform.devpractice.mapper.ProblemSolutionMapper;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemSolutionCommands;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemSolutionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ProblemSolutionController implements ProblemSolutionApi {

    private final ProblemSolutionService problemSolutionService;
    private final ProblemSolutionMapper problemSolutionMapper;

    @Override
    public ResponseEntity<List<AdminProblemSolutionResponse>> list(Integer problemId) {
        return ResponseEntity.ok(toResponses(problemSolutionService.listForAdmin(problemId)));
    }

    @Override
    public ResponseEntity<AdminProblemSolutionResponse> create(Integer problemId, ProblemSolutionRequest request) {
        ProblemSolution created = problemSolutionService.create(problemId,
                new ProblemSolutionCommands.Create(request.title(), request.explanation(), request.referenceSubmissionIds()));
        return ResponseEntity.status(HttpStatus.CREATED).body(problemSolutionMapper.toAdminResponse(created));
    }

    @Override
    public ResponseEntity<AdminProblemSolutionResponse> update(Integer problemId, Integer solutionId, ProblemSolutionRequest request) {
        ProblemSolution updated = problemSolutionService.update(problemId, solutionId,
                new ProblemSolutionCommands.Update(request.title(), request.explanation(), request.referenceSubmissionIds()));
        return ResponseEntity.ok(problemSolutionMapper.toAdminResponse(updated));
    }

    @Override
    public ResponseEntity<Void> delete(Integer problemId, Integer solutionId) {
        problemSolutionService.delete(problemId, solutionId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<AdminProblemSolutionResponse>> reorder(Integer problemId, ProblemSolutionOrderRequest request) {
        return ResponseEntity.ok(toResponses(problemSolutionService.reorder(problemId, request.solutionIds())));
    }

    private List<AdminProblemSolutionResponse> toResponses(List<ProblemSolution> solutions) {
        return solutions.stream().map(problemSolutionMapper::toAdminResponse).toList();
    }
}
