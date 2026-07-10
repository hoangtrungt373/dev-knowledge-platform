package com.ttg.devknowledgeplatform.content.service.impl;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.content.entity.Article;
import com.ttg.devknowledgeplatform.content.entity.Category;
import com.ttg.devknowledgeplatform.content.entity.ContentItem;
import com.ttg.devknowledgeplatform.content.entity.ContentItemTag;
import com.ttg.devknowledgeplatform.content.entity.Tag;
import com.ttg.devknowledgeplatform.content.enums.ContentStatus;
import com.ttg.devknowledgeplatform.content.enums.ContentType;
import com.ttg.devknowledgeplatform.content.enums.TagStatus;
import com.ttg.devknowledgeplatform.content.exception.ContentErrorCode;
import com.ttg.devknowledgeplatform.content.repository.ArticleRepository;
import com.ttg.devknowledgeplatform.content.repository.CategoryRepository;
import com.ttg.devknowledgeplatform.content.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.content.repository.TagRepository;
import com.ttg.devknowledgeplatform.content.repository.spec.ArticleSpecification;
import com.ttg.devknowledgeplatform.content.service.ArticleCommands;
import com.ttg.devknowledgeplatform.content.service.ArticleService;
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
public class ArticleServiceImpl implements ArticleService {

    private final ArticleRepository articleRepository;
    private final ContentItemRepository contentItemRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final SlugService slugService;

    @Override
    public Article create(ArticleCommands.Create command, Integer authorId) {
        validateArticleType(command.type());

        Category category = resolveCategory(command.categoryId());
        String slug = slugService.generateUniqueSlug(command.title(), contentItemRepository::existsBySlug, ContentErrorCode.ARTICLE_SLUG_CONFLICT);
        ContentStatus status = command.status() != null ? command.status() : ContentStatus.DRAFT;

        ContentItem contentItem = new ContentItem();
        contentItem.setType(command.type());
        contentItem.setTitle(command.title());
        contentItem.setSlug(slug);
        contentItem.setStatus(status);
        contentItem.setCategory(category);
        contentItem.setAuthorId(authorId);
        contentItem.setViewCount(0);
        if (ContentStatus.PUBLISHED.equals(status)) {
            contentItem.setPublishedAt(Instant.now());
        }

        ContentItem savedContentItem = contentItemRepository.save(contentItem);

        Set<Integer> tagIds = command.tagIds() == null ? Set.of() : command.tagIds();
        applyTagIds(savedContentItem, tagIds);

        Article article = new Article();
        article.setContentItem(savedContentItem);
        article.setBody(command.body());

        Article saved = articleRepository.save(article);
        log.info("Created article id={} slug={}", saved.getId(), slug);
        return saved;
    }

    @Override
    public Article update(Integer id, ArticleCommands.Update command) {
        Article article = findById(id);
        ContentItem contentItem = article.getContentItem();

        if (command.type() != null) {
            validateArticleType(command.type());
            contentItem.setType(command.type());
        }

        Category category = resolveCategory(command.categoryId());

        if (!contentItem.getTitle().equals(command.title())) {
            contentItem.setSlug(slugService.generateUniqueSlug(command.title(), contentItemRepository::existsBySlugAndIdNot, contentItem.getId(), ContentErrorCode.ARTICLE_SLUG_CONFLICT));
        }
        contentItem.setTitle(command.title());

        ContentStatus prevStatus = contentItem.getStatus();
        ContentStatus newStatus = command.status() != null ? command.status() : prevStatus;
        contentItem.setStatus(newStatus);
        contentItem.setCategory(category);

        if (ContentStatus.PUBLISHED.equals(newStatus) && !ContentStatus.PUBLISHED.equals(prevStatus)
                && contentItem.getPublishedAt() == null) {
            contentItem.setPublishedAt(Instant.now());
        }

        if (command.body() != null) {
            article.setBody(command.body());
        }

        if (command.tagIds() != null) {
            applyTagIds(contentItem, command.tagIds());
        }

        Article updated = articleRepository.save(article);
        log.info("Updated article id={}", id);
        return updated;
    }

    @Override
    public Article getById(Integer id) {
        return findById(id);
    }

    @Override
    public Article getBySlug(String slug) {
        return articleRepository.findByContentItem_Slug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ContentErrorCode.ARTICLE_NOT_FOUND, new Object[] {slug}));
    }

    @Override
    public Page<Article> list(Pageable pageable, ContentType type, ContentStatus status, String q) {
        Specification<Article> spec = ArticleSpecification.withFilters(type, status, q);
        return articleRepository.findAll(spec, pageable);
    }

    @Override
    public void delete(Integer id) {
        Article article = findById(id);
        ContentItem contentItem = article.getContentItem();
        articleRepository.delete(article);
        contentItemRepository.delete(contentItem);
        log.info("Deleted article id={} and its content item id={}", id, contentItem.getId());
    }

    private Article findById(Integer id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ContentErrorCode.ARTICLE_NOT_FOUND, new Object[] {id}));
    }

    private Category resolveCategory(Integer categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ContentErrorCode.CATEGORY_NOT_FOUND, new Object[] {categoryId}));
    }

    private static void validateArticleType(ContentType type) {
        if (type != ContentType.ARTICLE && type != ContentType.BLOG_POST) {
            throw new ApiException(ContentErrorCode.ARTICLE_TYPE_INVALID, new Object[] {type});
        }
    }

    private void applyTagIds(ContentItem contentItem, Set<Integer> tagIds) {
        if (tagIds.stream().anyMatch(Objects::isNull)) {
            throw new ApiException(CommonErrorCode.VALIDATION_FIELD_INVALID, "tagIds must not contain null");
        }
        LinkedHashSet<Integer> unique = new LinkedHashSet<>(tagIds);
        if (unique.isEmpty()) {
            contentItem.getContentItemTags().clear();
            return;
        }

        List<Tag> existing = tagRepository.findAllById(unique);
        if (existing.size() != unique.size()) {
            throw new ApiException(ContentErrorCode.TAG_NOT_FOUND, "One or more tags were not found");
        }
        List<Integer> inactive = existing.stream()
                .filter(t -> t.getStatus() != TagStatus.ACTIVE)
                .map(Tag::getId)
                .toList();
        if (!inactive.isEmpty()) {
            throw new ApiException(CommonErrorCode.VALIDATION_FIELD_INVALID,
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
