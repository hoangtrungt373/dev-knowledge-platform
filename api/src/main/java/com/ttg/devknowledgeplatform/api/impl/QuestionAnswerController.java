package com.ttg.devknowledgeplatform.api.impl;

import com.ttg.devknowledgeplatform.api.QuestionAnswerApi;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.QuestionDifficulty;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateQuestionAnswerRequest;
import com.ttg.devknowledgeplatform.dto.admin.QuestionAnswerResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateQuestionAnswerRequest;
import com.ttg.devknowledgeplatform.service.QuestionAnswerService;
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
 * Implementation of {@link QuestionAnswerApi}.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class QuestionAnswerController implements QuestionAnswerApi {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "dteCreation", "difficulty");

    private final QuestionAnswerService questionAnswerService;
    private final UserService userService;

    @Override
    public ResponseEntity<QuestionAnswerResponse> create(
            CustomOAuth2User principal, CreateQuestionAnswerRequest request) {
        Integer authorId = resolveAuthorId(principal);
        QuestionAnswerResponse response = questionAnswerService.create(request, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<QuestionAnswerResponse> update(Integer id, UpdateQuestionAnswerRequest request) {
        QuestionAnswerResponse response = questionAnswerService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> delete(Integer id) {
        questionAnswerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<QuestionAnswerResponse> getById(Integer id) {
        QuestionAnswerResponse response = questionAnswerService.getById(id);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<PagedResponse<QuestionAnswerResponse>> list(
            int page, int size, String sortBy, String sortDir,
            QuestionDifficulty difficulty, ContentStatus status, Boolean isCommon, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        PagedResponse<QuestionAnswerResponse> response =
                questionAnswerService.list(pageable, difficulty, status, isCommon, q);
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
