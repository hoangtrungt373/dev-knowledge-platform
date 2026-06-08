package com.ttg.devknowledgeplatform.service;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.InterviewQuestionDifficulty;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateInterviewQuestionRequest;
import com.ttg.devknowledgeplatform.dto.admin.InterviewQuestionResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateInterviewQuestionRequest;
import org.springframework.data.domain.Pageable;

public interface InterviewQuestionService {

    InterviewQuestionResponse create(CreateInterviewQuestionRequest request, Integer authorId);

    InterviewQuestionResponse update(Integer id, UpdateInterviewQuestionRequest request);

    InterviewQuestionResponse getById(Integer id);

    InterviewQuestionResponse getBySlug(String slug);

    PagedResponse<InterviewQuestionResponse> list(
            Pageable pageable,
            InterviewQuestionDifficulty difficulty,
            ContentStatus status,
            Boolean isCommon,
            String q);

    void delete(Integer id);
}