package com.ttg.devknowledgeplatform.api.impl;

import com.ttg.devknowledgeplatform.api.QuestionAnswerApi;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.content.entity.QuestionAnswer;
import com.ttg.devknowledgeplatform.content.enums.ContentStatus;
import com.ttg.devknowledgeplatform.content.enums.QuestionDifficulty;
import com.ttg.devknowledgeplatform.content.service.QuestionAnswerCommands;
import com.ttg.devknowledgeplatform.content.service.QuestionAnswerService;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.content.CreateQuestionAnswerRequest;
import com.ttg.devknowledgeplatform.dto.content.QuestionAnswerResponse;
import com.ttg.devknowledgeplatform.dto.content.UpdateQuestionAnswerRequest;
import com.ttg.devknowledgeplatform.mapper.QuestionAnswerMapper;
import com.ttg.devknowledgeplatform.security.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
    private final QuestionAnswerMapper questionAnswerMapper;
    private final UserService userService;

    @Override
    public ResponseEntity<QuestionAnswerResponse> create(
            CustomOAuth2User principal, CreateQuestionAnswerRequest request) {
        Integer authorId = resolveAuthorId(principal);
        QuestionAnswerCommands.Create command = new QuestionAnswerCommands.Create(
                request.getTitle(), request.getDifficulty(), request.getQuestionBody(),
                request.getShortAnswer(), request.getDetailedAnswer(), request.getIsCommon(),
                request.getStatus(), request.getCategoryId(), request.getTagIds());
        QuestionAnswer created = questionAnswerService.create(command, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(questionAnswerMapper.toResponse(created));
    }

    @Override
    public ResponseEntity<QuestionAnswerResponse> update(Integer id, UpdateQuestionAnswerRequest request) {
        QuestionAnswerCommands.Update command = new QuestionAnswerCommands.Update(
                request.getTitle(), request.getDifficulty(), request.getQuestionBody(),
                request.getShortAnswer(), request.getDetailedAnswer(), request.getIsCommon(),
                request.getStatus(), request.getCategoryId(), request.getTagIds());
        QuestionAnswer updated = questionAnswerService.update(id, command);
        return ResponseEntity.ok(questionAnswerMapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<Void> delete(Integer id) {
        questionAnswerService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<QuestionAnswerResponse> getById(Integer id) {
        return ResponseEntity.ok(questionAnswerMapper.toResponse(questionAnswerService.getById(id)));
    }

    @Override
    public ResponseEntity<PagedResponse<QuestionAnswerResponse>> list(
            int page, int size, String sortBy, String sortDir,
            QuestionDifficulty difficulty, ContentStatus status, Boolean isCommon, String q) {
        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        Page<QuestionAnswerResponse> responses =
                questionAnswerService.list(pageable, difficulty, status, isCommon, q)
                        .map(questionAnswerMapper::toResponse);
        return ResponseEntity.ok(PagedResponse.from(responses));
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
