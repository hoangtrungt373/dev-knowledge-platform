package com.ttg.devknowledgeplatform.endpoint;

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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/interview-questions")
@RequiredArgsConstructor
@Slf4j
public class InterviewQuestionEndpoint {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "dteCreation", "difficulty");

    private final InterviewQuestionService interviewQuestionService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<InterviewQuestionResponse> create(
            @AuthenticationPrincipal CustomOAuth2User principal,
            @Valid @RequestBody CreateInterviewQuestionRequest request) {
        Integer authorId = resolveAuthorId(principal);
        InterviewQuestionResponse response = interviewQuestionService.create(request, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InterviewQuestionResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateInterviewQuestionRequest request) {
        InterviewQuestionResponse response = interviewQuestionService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        interviewQuestionService.delete(id);
        return ResponseEntity.noContent().build();
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
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) InterviewQuestionDifficulty difficulty,
            @RequestParam(required = false) ContentStatus status,
            @RequestParam(required = false) Boolean isCommon,
            @RequestParam(required = false) String q) {

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
