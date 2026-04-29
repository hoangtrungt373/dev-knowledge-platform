package com.ttg.devknowledgeplatform.service.impl;

import com.ttg.devknowledgeplatform.common.entity.Category;
import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import com.ttg.devknowledgeplatform.common.entity.InterviewQuestion;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.enums.InterviewQuestionDifficulty;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateInterviewQuestionRequest;
import com.ttg.devknowledgeplatform.dto.admin.InterviewQuestionResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateInterviewQuestionRequest;
import com.ttg.devknowledgeplatform.repository.CategoryRepository;
import com.ttg.devknowledgeplatform.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.repository.InterviewQuestionRepository;
import com.ttg.devknowledgeplatform.repository.spec.InterviewQuestionSpecification;
import com.ttg.devknowledgeplatform.service.InterviewQuestionService;
import com.ttg.devknowledgeplatform.service.SlugService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class InterviewQuestionServiceImpl implements InterviewQuestionService {

    private final InterviewQuestionRepository interviewQuestionRepository;
    private final ContentItemRepository contentItemRepository;
    private final CategoryRepository categoryRepository;
    private final SlugService slugService;

    @Override
    public InterviewQuestionResponse create(CreateInterviewQuestionRequest request) {
        Category category = resolveCategory(request.getCategoryId());
        String slug = slugService.generateUniqueSlug(request.getTitle());

        ContentItem contentItem = new ContentItem();
        contentItem.setType(ContentType.INTERVIEW_QUESTION);
        contentItem.setTitle(request.getTitle());
        contentItem.setSlug(slug);
        contentItem.setStatus(request.getStatus() != null ? request.getStatus() : ContentStatus.DRAFT);
        contentItem.setCategory(category);
        contentItem.setViewCount(0);
        if (ContentStatus.PUBLISHED.equals(contentItem.getStatus())) {
            contentItem.setPublishedAt(Instant.now());
        }

        ContentItem savedContentItem = contentItemRepository.save(contentItem);

        InterviewQuestion question = new InterviewQuestion();
        question.setContentItem(savedContentItem);
        question.setDifficulty(request.getDifficulty());
        question.setQuestionBody(request.getQuestionBody());
        question.setShortAnswer(request.getShortAnswer());
        question.setDetailedAnswer(request.getDetailedAnswer());
        question.setIsCommon(request.getIsCommon() != null ? request.getIsCommon() : false);

        InterviewQuestion saved = interviewQuestionRepository.save(question);
        log.info("Created interview question id={} slug={}", saved.getId(), slug);
        return toResponse(saved);
    }

    @Override
    public InterviewQuestionResponse update(Integer id, UpdateInterviewQuestionRequest request) {
        InterviewQuestion question = findById(id);
        ContentItem contentItem = question.getContentItem();

        Category category = resolveCategory(request.getCategoryId());

        if (!contentItem.getTitle().equals(request.getTitle())) {
            String newSlug = slugService.generateUniqueSlug(request.getTitle(), contentItem.getId());
            contentItem.setSlug(newSlug);
        }

        ContentStatus prevStatus = contentItem.getStatus();
        ContentStatus newStatus = request.getStatus() != null ? request.getStatus() : prevStatus;

        contentItem.setTitle(request.getTitle());
        contentItem.setStatus(newStatus);
        contentItem.setCategory(category);

        if (ContentStatus.PUBLISHED.equals(newStatus) && !ContentStatus.PUBLISHED.equals(prevStatus)
                && contentItem.getPublishedAt() == null) {
            contentItem.setPublishedAt(Instant.now());
        }

        question.setDifficulty(request.getDifficulty());
        question.setQuestionBody(request.getQuestionBody());
        question.setShortAnswer(request.getShortAnswer());
        question.setDetailedAnswer(request.getDetailedAnswer());
        question.setIsCommon(request.getIsCommon() != null ? request.getIsCommon() : question.getIsCommon());

        InterviewQuestion updated = interviewQuestionRepository.save(question);
        log.info("Updated interview question id={}", id);
        return toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewQuestionResponse getById(Integer id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<InterviewQuestionResponse> list(
            Pageable pageable,
            InterviewQuestionDifficulty difficulty,
            ContentStatus status,
            Boolean isCommon,
            String q) {

        Specification<InterviewQuestion> spec =
                InterviewQuestionSpecification.withFilters(difficulty, status, isCommon, q);
        Page<InterviewQuestionResponse> page =
                interviewQuestionRepository.findAll(spec, pageable).map(this::toResponse);
        return PagedResponse.from(page);
    }

    private InterviewQuestion findById(Integer id) {
        return interviewQuestionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.INTERVIEW_QUESTION_NOT_FOUND,
                        "Interview question not found with id: " + id));
    }

    private Category resolveCategory(Integer categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CATEGORY_NOT_FOUND,
                        "Category not found with id: " + categoryId));
    }

    private InterviewQuestionResponse toResponse(InterviewQuestion question) {
        ContentItem ci = question.getContentItem();
        return InterviewQuestionResponse.builder()
                .id(question.getId())
                .contentItemId(ci.getId())
                .title(ci.getTitle())
                .slug(ci.getSlug())
                .difficulty(question.getDifficulty())
                .questionBody(question.getQuestionBody())
                .shortAnswer(question.getShortAnswer())
                .detailedAnswer(question.getDetailedAnswer())
                .isCommon(question.getIsCommon())
                .status(ci.getStatus())
                .categoryId(ci.getCategory() != null ? ci.getCategory().getId() : null)
                .createdAt(question.getDteCreation())
                .updatedAt(question.getDteLastModification())
                .build();
    }
}