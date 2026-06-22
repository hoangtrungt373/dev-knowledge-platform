package com.ttg.devknowledgeplatform.api.impl;

import com.ttg.devknowledgeplatform.api.InterviewQuestionApi;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.InterviewQuestionDifficulty;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateInterviewQuestionRequest;
import com.ttg.devknowledgeplatform.dto.admin.InterviewQuestionResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateInterviewQuestionRequest;
import com.ttg.devknowledgeplatform.service.InterviewQuestionService;
import com.ttg.devknowledgeplatform.security.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * Implementation of {@link InterviewQuestionApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class InterviewQuestionController implements InterviewQuestionApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "dteCreation", "difficulty");

    private final InterviewQuestionService interviewQuestionService;
    private final UserService userService;

    @Override
    public ResponseEntity<InterviewQuestionResponse> create(
            CustomOAuth2User principal, CreateInterviewQuestionRequest request) {
        Integer authorId = resolveAuthorId(principal);
        InterviewQuestionResponse response = interviewQuestionService.create(request, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<InterviewQuestionResponse> update(Integer id, UpdateInterviewQuestionRequest request) {
        InterviewQuestionResponse response = interviewQuestionService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> delete(Integer id) {
        interviewQuestionService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<InterviewQuestionResponse> getById(Integer id) {
        InterviewQuestionResponse response = interviewQuestionService.getById(id);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<InterviewQuestionResponse>> list(
            int page, int size, String sortBy, String sortDir,
            InterviewQuestionDifficulty difficulty, ContentStatus status, Boolean isCommon, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        PagedResponse<InterviewQuestionResponse> response =
                interviewQuestionService.list(pageable, difficulty, status, isCommon, q);
        return ResponseEntity.ok(response);
    }

    private Integer resolveAuthorId(CustomOAuth2User principal) {
        if (principal == null) return null;
        User user = userService.findByEmail(principal.getEmail());
        return user != null ? user.getId() : null;
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
