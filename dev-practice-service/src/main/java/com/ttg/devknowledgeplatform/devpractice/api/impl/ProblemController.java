package com.ttg.devknowledgeplatform.devpractice.api.impl;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.api.ProblemApi;
import com.ttg.devknowledgeplatform.devpractice.dto.CreateProblemRequest;
import com.ttg.devknowledgeplatform.devpractice.dto.MethodParameterRequest;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSummaryResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.TestCaseRequest;
import com.ttg.devknowledgeplatform.devpractice.dto.UpdateProblemRequest;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.enums.Difficulty;
import com.ttg.devknowledgeplatform.devpractice.mapper.ProblemMapper;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemCommands;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ProblemController implements ProblemApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "dteCreation");

    private final ProblemService problemService;
    private final ProblemMapper problemMapper;

    @Override
    public ResponseEntity<ProblemResponse> create(String authorUuid, CreateProblemRequest request) {
        ProblemCommands.Create command = new ProblemCommands.Create(
                request.getTitle(), request.getDescription(), request.getDifficulty(), request.getStatus(),
                request.getMethodName(), request.getReturnType(), toParameterInputs(request.getParameters()),
                toTestCaseInputs(request.getTestCases()));
        Problem created = problemService.create(command, authorUuid);
        return ResponseEntity.status(HttpStatus.CREATED).body(problemMapper.toResponse(created));
    }

    @Override
    public ResponseEntity<ProblemResponse> update(Integer id, UpdateProblemRequest request) {
        ProblemCommands.Update command = new ProblemCommands.Update(
                request.getTitle(), request.getDescription(), request.getDifficulty(), request.getStatus(),
                request.getMethodName(), request.getReturnType(), toParameterInputs(request.getParameters()),
                toTestCaseInputs(request.getTestCases()));
        Problem updated = problemService.update(id, command);
        return ResponseEntity.ok(problemMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<Void> delete(Integer id) {
        problemService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ProblemResponse> getById(Integer id) {
        return ResponseEntity.ok(problemMapper.toResponse(problemService.getById(id)));
    }

    @Override
    public ResponseEntity<PagedResponse<ProblemSummaryResponse>> list(
            int page, int size, String sortBy, String sortDir, Difficulty difficulty, ContentStatus status, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        Page<ProblemSummaryResponse> responses = problemService.list(pageable, difficulty, status, q)
                .map(problemMapper::toSummaryResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
    }

    private static List<ProblemCommands.TestCaseInput> toTestCaseInputs(List<TestCaseRequest> requests) {
        return requests.stream()
                .map(r -> new ProblemCommands.TestCaseInput(r.getInput(), r.getExpectedOutput(), r.getSample()))
                .toList();
    }

    /** A parameter's position is its index in the supplied list, not a client-specified field. */
    private static List<ProblemCommands.MethodParameterInput> toParameterInputs(List<MethodParameterRequest> requests) {
        return IntStream.range(0, requests.size())
                .mapToObj(i -> new ProblemCommands.MethodParameterInput(
                        requests.get(i).getName(), requests.get(i).getType(), i))
                .toList();
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
