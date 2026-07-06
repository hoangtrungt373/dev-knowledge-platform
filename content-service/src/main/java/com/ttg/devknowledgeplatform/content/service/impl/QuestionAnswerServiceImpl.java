package com.ttg.devknowledgeplatform.content.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.content.entity.Category;
import com.ttg.devknowledgeplatform.content.entity.ContentItem;
import com.ttg.devknowledgeplatform.content.entity.ContentItemTag;
import com.ttg.devknowledgeplatform.content.entity.QuestionAnswer;
import com.ttg.devknowledgeplatform.content.entity.Tag;
import com.ttg.devknowledgeplatform.content.enums.ContentStatus;
import com.ttg.devknowledgeplatform.content.enums.ContentType;
import com.ttg.devknowledgeplatform.content.enums.QuestionDifficulty;
import com.ttg.devknowledgeplatform.content.enums.TagStatus;
import com.ttg.devknowledgeplatform.content.exception.ContentErrorCode;
import com.ttg.devknowledgeplatform.content.repository.CategoryRepository;
import com.ttg.devknowledgeplatform.content.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.content.repository.QuestionAnswerRepository;
import com.ttg.devknowledgeplatform.content.repository.TagRepository;
import com.ttg.devknowledgeplatform.content.repository.spec.QuestionAnswerSpecification;
import com.ttg.devknowledgeplatform.content.service.QuestionAnswerCommands;
import com.ttg.devknowledgeplatform.content.service.QuestionAnswerService;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

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

    @Override
    public QuestionAnswer create(QuestionAnswerCommands.Create command, Integer authorId) {
        Category category = resolveCategory(command.categoryId());
        String slug = slugService.generateUniqueSlug(command.title(), contentItemRepository::existsBySlug, ContentErrorCode.QUESTION_ANSWER_SLUG_CONFLICT);

        ContentItem contentItem = new ContentItem();
        contentItem.setType(ContentType.QUESTION_ANSWER);
        contentItem.setTitle(command.title());
        contentItem.setSlug(slug);
        contentItem.setStatus(command.status() != null ? command.status() : ContentStatus.DRAFT);
        contentItem.setCategory(category);
        contentItem.setAuthorId(authorId);
        contentItem.setViewCount(0);
        if (ContentStatus.PUBLISHED.equals(contentItem.getStatus())) {
            contentItem.setPublishedAt(Instant.now());
        }

        ContentItem savedContentItem = contentItemRepository.save(contentItem);

        Set<Integer> tagIdsToApply = command.tagIds() == null ? Set.of() : command.tagIds();
        applyTagIds(savedContentItem, tagIdsToApply);

        QuestionAnswer question = new QuestionAnswer();
        question.setContentItem(savedContentItem);
        question.setDifficulty(command.difficulty());
        question.setQuestionBody(command.questionBody());
        question.setShortAnswer(command.shortAnswer());
        question.setDetailedAnswer(command.detailedAnswer());
        question.setIsCommon(command.isCommon());

        QuestionAnswer saved = questionAnswerRepository.save(question);
        log.info("Created question id={} slug={}", saved.getId(), slug);
        return saved;
    }

    @Override
    public QuestionAnswer update(Integer id, QuestionAnswerCommands.Update command) {
        QuestionAnswer question = findById(id);
        ContentItem contentItem = question.getContentItem();

        Category category = resolveCategory(command.categoryId());

        if (!contentItem.getTitle().equals(command.title())) {
            String newSlug = slugService.generateUniqueSlug(command.title(), contentItemRepository::existsBySlugAndIdNot, contentItem.getId(), ContentErrorCode.QUESTION_ANSWER_SLUG_CONFLICT);
            contentItem.setSlug(newSlug);
        }

        ContentStatus prevStatus = contentItem.getStatus();
        ContentStatus newStatus = command.status() != null ? command.status() : prevStatus;

        contentItem.setTitle(command.title());
        contentItem.setStatus(newStatus);
        contentItem.setCategory(category);

        if (ContentStatus.PUBLISHED.equals(newStatus) && !ContentStatus.PUBLISHED.equals(prevStatus)
                && contentItem.getPublishedAt() == null) {
            contentItem.setPublishedAt(Instant.now());
        }

        question.setDifficulty(command.difficulty());
        question.setQuestionBody(command.questionBody());
        question.setShortAnswer(command.shortAnswer());
        question.setDetailedAnswer(command.detailedAnswer());
        question.setIsCommon(command.isCommon() != null ? command.isCommon() : question.getIsCommon());

        if (command.tagIds() != null) {
            applyTagIds(contentItem, command.tagIds());
        }

        QuestionAnswer updated = questionAnswerRepository.save(question);
        log.info("Updated question id={}", id);
        return updated;
    }

    @Override
    public QuestionAnswer getById(Integer id) {
        return findById(id);
    }

    @Override
    public QuestionAnswer getBySlug(String slug) {
        return questionAnswerRepository.findByContentItem_Slug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ContentErrorCode.QUESTION_ANSWER_NOT_FOUND,
                        "Question not found with slug: " + slug));
    }

    @Override
    public Page<QuestionAnswer> list(
            Pageable pageable,
            QuestionDifficulty difficulty,
            ContentStatus status,
            Boolean isCommon,
            String q) {

        Specification<QuestionAnswer> spec =
                QuestionAnswerSpecification.withFilters(difficulty, status, isCommon, q);
        return questionAnswerRepository.findAll(spec, pageable);
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
                        ContentErrorCode.QUESTION_ANSWER_NOT_FOUND,
                        "Question not found with id: " + id));
    }

    private Category resolveCategory(Integer categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ContentErrorCode.CATEGORY_NOT_FOUND,
                        "Category not found with id: " + categoryId));
    }

    private void applyTagIds(ContentItem contentItem, Set<Integer> tagIds) {
        if (tagIds.stream().anyMatch(Objects::isNull)) {
            throw new ApiException(
                    CommonErrorCode.VALIDATION_FIELD_INVALID, "tagIds must not contain null");
        }
        LinkedHashSet<Integer> unique = new LinkedHashSet<>(tagIds);
        if (unique.isEmpty()) {
            contentItem.getContentItemTags().clear();
            return;
        }

        List<Tag> existing = tagRepository.findAllById(unique);
        if (existing.size() != unique.size()) {
            throw new ApiException(
                    ContentErrorCode.TAG_NOT_FOUND, "One or more tags were not found");
        }
        List<Integer> inactive = existing.stream()
                .filter(t -> t.getStatus() != TagStatus.ACTIVE)
                .map(Tag::getId)
                .toList();
        if (!inactive.isEmpty()) {
            throw new ApiException(
                    CommonErrorCode.VALIDATION_FIELD_INVALID,
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
