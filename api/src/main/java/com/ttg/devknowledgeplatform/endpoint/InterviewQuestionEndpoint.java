package com.ttg.devknowledgeplatform.endpoint;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.InterviewQuestionDifficulty;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateInterviewQuestionRequest;
import com.ttg.devknowledgeplatform.dto.admin.InterviewQuestionResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateInterviewQuestionRequest;
import com.ttg.devknowledgeplatform.service.InterviewQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/interview-questions")
@RequiredArgsConstructor
@Slf4j
public class InterviewQuestionEndpoint {

    private final InterviewQuestionService interviewQuestionService;

    @PostMapping
    public ResponseEntity<InterviewQuestionResponse> create(
            @Valid @RequestBody CreateInterviewQuestionRequest request) {
        InterviewQuestionResponse response = interviewQuestionService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InterviewQuestionResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateInterviewQuestionRequest request) {
        InterviewQuestionResponse response = interviewQuestionService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InterviewQuestionResponse> getById(@PathVariable Integer id) {
        InterviewQuestionResponse response = interviewQuestionService.getById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<InterviewQuestionResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) InterviewQuestionDifficulty difficulty,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) Boolean isCommon,
            @RequestParam(required = false) String q) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        PagedResponse<InterviewQuestionResponse> response =
                interviewQuestionService.list(pageable, difficulty, status, isCommon, q);
        return ResponseEntity.ok(response);
    }
}