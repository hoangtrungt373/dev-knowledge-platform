package com.ttg.devknowledgeplatform.service.impl;

import com.ttg.devknowledgeplatform.common.entity.Category;
import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import com.ttg.devknowledgeplatform.common.entity.ContentItemTag;
import com.ttg.devknowledgeplatform.common.entity.QuestionAnswer;
import com.ttg.devknowledgeplatform.common.entity.Tag;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.enums.QuestionDifficulty;
import com.ttg.devknowledgeplatform.common.enums.TagStatus;
import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateQuestionAnswerRequest;
import com.ttg.devknowledgeplatform.dto.admin.QuestionAnswerResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateQuestionAnswerRequest;
import com.ttg.devknowledgeplatform.mapper.QuestionAnswerMapper;
import com.ttg.devknowledgeplatform.repository.CategoryRepository;
import com.ttg.devknowledgeplatform.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.repository.QuestionAnswerRepository;
import com.ttg.devknowledgeplatform.repository.TagRepository;
import com.ttg.devknowledgeplatform.repository.spec.QuestionAnswerSpecification;
import com.ttg.devknowledgeplatform.service.QuestionAnswerService;
import com.ttg.devknowledgeplatform.service.SlugService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class QuestionAnswerServiceImpl implements QuestionAnswerService {

    private final QuestionAnswerRepository questionAnswerRepository;
    private final ContentItemRepository contentItemRepository;
    private final CategoryRepository categoryRepository;
    private final SlugService slugService;
    private final TagRepository tagRepository;
    private final QuestionAnswerMapper questionAnswerMapper;

    @Override
    public QuestionAnswerResponse create(CreateQuestionAnswerRequest request, Integer authorId) {
        Category category = resolveCategory(request.getCategoryId());
        String slug = slugService.generateUniqueSlug(request.getTitle(), contentItemRepository::existsBySlug, ErrorCode.QUESTION_ANSWER_SLUG_CONFLICT);

        ContentItem contentItem = new ContentItem();
        contentItem.setType(ContentType.QUESTION_ANSWER);
        contentItem.setTitle(request.getTitle());
        contentItem.setSlug(slug);
        contentItem.setStatus(request.getStatus() != null ? request.getStatus() : ContentStatus.DRAFT);
        contentItem.setCategory(category);
        contentItem.setAuthorId(authorId);
        contentItem.setViewCount(0);
        if (ContentStatus.PUBLISHED.equals(contentItem.getStatus())) {
            contentItem.setPublishedAt(Instant.now());
        }

        ContentItem savedContentItem = contentItemRepository.save(contentItem);

        Set<Integer> tagIdsToApply =
                request.getTagIds() == null ? Set.of() : request.getTagIds();
        applyTagIds(savedContentItem, tagIdsToApply);

        QuestionAnswer question = new QuestionAnswer();
        question.setContentItem(savedContentItem);
        question.setDifficulty(request.getDifficulty());
        question.setQuestionBody(request.getQuestionBody());
        question.setShortAnswer(request.getShortAnswer());
        question.setDetailedAnswer(request.getDetailedAnswer());
        question.setIsCommon(request.getIsCommon());

        QuestionAnswer saved = questionAnswerRepository.save(question);
        log.info("Created question id={} slug={}", saved.getId(), slug);
        return questionAnswerMapper.toResponse(saved);
    }

    @Override
    public QuestionAnswerResponse update(Integer id, UpdateQuestionAnswerRequest request) {
        QuestionAnswer question = findById(id);
        ContentItem contentItem = question.getContentItem();

        Category category = resolveCategory(request.getCategoryId());

        if (!contentItem.getTitle().equals(request.getTitle())) {
            String newSlug = slugService.generateUniqueSlug(request.getTitle(), contentItemRepository::existsBySlugAndIdNot, contentItem.getId(), ErrorCode.QUESTION_ANSWER_SLUG_CONFLICT);
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

        if (request.getTagIds() != null) {
            applyTagIds(contentItem, request.getTagIds());
        }

        QuestionAnswer updated = questionAnswerRepository.save(question);
        log.info("Updated question id={}", id);
        return questionAnswerMapper.toResponse(updated);
    }

    @Override
    public QuestionAnswerResponse getById(Integer id) {
        return questionAnswerMapper.toResponse(findById(id));
    }

    @Override
    public QuestionAnswerResponse getBySlug(String slug) {
        QuestionAnswer question = questionAnswerRepository.findByContentItem_Slug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.QUESTION_ANSWER_NOT_FOUND,
                        "Question not found with slug: " + slug));
        return questionAnswerMapper.toResponse(question);
    }

    @Override
    public PagedResponse<QuestionAnswerResponse> list(
            Pageable pageable,
            QuestionDifficulty difficulty,
            ContentStatus status,
            Boolean isCommon,
            String q) {

        Specification<QuestionAnswer> spec =
                QuestionAnswerSpecification.withFilters(difficulty, status, isCommon, q);
        Page<QuestionAnswerResponse> page =
                questionAnswerRepository.findAll(spec, pageable).map(questionAnswerMapper::toResponse);
        return PagedResponse.from(page);
    }

    @Override
    public void delete(Integer id) {
        QuestionAnswer question = findById(id);
        ContentItem contentItem = question.getContentItem();
        questionAnswerRepository.delete(question);
        contentItemRepository.delete(contentItem);
        log.info("Deleted question id={} and its content item id={}", id, contentItem.getId());
    }

    private QuestionAnswer findById(Integer id) {
        return questionAnswerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.QUESTION_ANSWER_NOT_FOUND,
                        "Question not found with id: " + id));
    }

    private Category resolveCategory(Integer categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CATEGORY_NOT_FOUND,
                        "Category not found with id: " + categoryId));
    }

    private void applyTagIds(ContentItem contentItem, Set<Integer> tagIds) {
        if (tagIds.stream().anyMatch(Objects::isNull)) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FIELD_INVALID, "tagIds must not contain null");
        }
        LinkedHashSet<Integer> unique = new LinkedHashSet<>(tagIds);
        if (unique.isEmpty()) {
            contentItem.getContentItemTags().clear();
            return;
        }

        List<Tag> existing = tagRepository.findAllById(unique);
        if (existing.size() != unique.size()) {
            throw new ApiException(
                    ErrorCode.TAG_NOT_FOUND, "One or more tags were not found");
        }
        List<Integer> inactive = existing.stream()
                .filter(t -> t.getStatus() != TagStatus.ACTIVE)
                .map(Tag::getId)
                .toList();
        if (!inactive.isEmpty()) {
            throw new ApiException(
                    ErrorCode.VALIDATION_FIELD_INVALID,
                    "Tags with ids " + inactive + " are inactive and cannot be assigned");
        }

        contentItem.getContentItemTags().clear();
        for (Integer tagId : unique) {
            ContentItemTag link = new ContentItemTag();
            link.setContentItem(contentItem);
            link.setTag(tagRepository.getReferenceById(tagId));
            contentItem.getContentItemTags().add(link);
        }
    }

}
