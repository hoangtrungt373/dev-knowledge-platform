package com.ttg.devknowledgeplatform.service.impl;

import com.ttg.devknowledgeplatform.common.entity.Article;
import com.ttg.devknowledgeplatform.common.entity.Category;
import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import com.ttg.devknowledgeplatform.common.entity.ContentItemTag;
import com.ttg.devknowledgeplatform.common.entity.Tag;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.enums.TagStatus;
import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.common.exception.ErrorCode;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.ArticleResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateArticleRequest;
import com.ttg.devknowledgeplatform.dto.admin.UpdateArticleRequest;
import com.ttg.devknowledgeplatform.repository.ArticleRepository;
import com.ttg.devknowledgeplatform.repository.CategoryRepository;
import com.ttg.devknowledgeplatform.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.repository.TagRepository;
import com.ttg.devknowledgeplatform.repository.spec.ArticleSpecification;
import com.ttg.devknowledgeplatform.service.ArticleService;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ArticleServiceImpl implements ArticleService {

    private final ArticleRepository articleRepository;
    private final ContentItemRepository contentItemRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final SlugService slugService;

    @Override
    public ArticleResponse create(CreateArticleRequest request, Integer authorId) {
        validateArticleType(request.getType());

        Category category = resolveCategory(request.getCategoryId());
        String slug = slugService.generateUniqueSlug(request.getTitle());
        ContentStatus status = request.getStatus() != null ? request.getStatus() : ContentStatus.DRAFT;

        ContentItem contentItem = new ContentItem();
        contentItem.setType(request.getType());
        contentItem.setTitle(request.getTitle());
        contentItem.setSlug(slug);
        contentItem.setStatus(status);
        contentItem.setCategory(category);
        contentItem.setAuthorId(authorId);
        contentItem.setViewCount(0);
        if (ContentStatus.PUBLISHED.equals(status)) {
            contentItem.setPublishedAt(Instant.now());
        }

        ContentItem savedContentItem = contentItemRepository.save(contentItem);

        Set<Integer> tagIds = request.getTagIds() == null ? Set.of() : request.getTagIds();
        applyTagIds(savedContentItem, tagIds);

        Article article = new Article();
        article.setContentItem(savedContentItem);
        article.setBody(request.getBody());

        Article saved = articleRepository.save(article);
        log.info("Created article id={} slug={}", saved.getId(), slug);
        return toResponse(saved);
    }

    @Override
    public ArticleResponse update(Integer id, UpdateArticleRequest request) {
        Article article = findById(id);
        ContentItem contentItem = article.getContentItem();

        if (request.getType() != null) {
            validateArticleType(request.getType());
            contentItem.setType(request.getType());
        }

        Category category = resolveCategory(request.getCategoryId());

        if (!contentItem.getTitle().equals(request.getTitle())) {
            contentItem.setSlug(slugService.generateUniqueSlug(request.getTitle(), contentItem.getId()));
        }
        contentItem.setTitle(request.getTitle());

        ContentStatus prevStatus = contentItem.getStatus();
        ContentStatus newStatus = request.getStatus() != null ? request.getStatus() : prevStatus;
        contentItem.setStatus(newStatus);
        contentItem.setCategory(category);

        if (ContentStatus.PUBLISHED.equals(newStatus) && !ContentStatus.PUBLISHED.equals(prevStatus)
                && contentItem.getPublishedAt() == null) {
            contentItem.setPublishedAt(Instant.now());
        }

        if (request.getBody() != null) {
            article.setBody(request.getBody());
        }

        if (request.getTagIds() != null) {
            applyTagIds(contentItem, request.getTagIds());
        }

        Article updated = articleRepository.save(article);
        log.info("Updated article id={}", id);
        return toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleResponse getById(Integer id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ArticleResponse getBySlug(String slug) {
        Article article = articleRepository.findByContentItem_Slug(slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ARTICLE_NOT_FOUND, "Article not found with slug: " + slug));
        return toResponse(article);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ArticleResponse> list(
            Pageable pageable, ContentType type, ContentStatus status, String q) {
        Specification<Article> spec = ArticleSpecification.withFilters(type, status, q);
        Page<ArticleResponse> page = articleRepository.findAll(spec, pageable).map(this::toResponse);
        return PagedResponse.from(page);
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
                        ErrorCode.ARTICLE_NOT_FOUND, "Article not found with id: " + id));
    }

    private Category resolveCategory(Integer categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.CATEGORY_NOT_FOUND, "Category not found with id: " + categoryId));
    }

    private static void validateArticleType(ContentType type) {
        if (type != ContentType.ARTICLE && type != ContentType.BLOG_POST) {
            throw new ApiException(ErrorCode.ARTICLE_TYPE_INVALID,
                    "Type must be ARTICLE or BLOG_POST, got: " + type);
        }
    }

    private ArticleResponse toResponse(Article article) {
        ContentItem ci = article.getContentItem();
        return ArticleResponse.builder()
                .id(article.getId())
                .contentItemId(ci.getId())
                .title(ci.getTitle())
                .slug(ci.getSlug())
                .type(ci.getType())
                .body(article.getBody())
                .status(ci.getStatus())
                .categoryId(ci.getCategory() != null ? ci.getCategory().getId() : null)
                .tagIds(extractTagIds(ci))
                .viewCount(ci.getViewCount())
                .publishedAt(ci.getPublishedAt())
                .createdAt(article.getDteCreation())
                .updatedAt(article.getDteLastModification())
                .build();
    }

    private void applyTagIds(ContentItem contentItem, Set<Integer> tagIds) {
        if (tagIds.stream().anyMatch(Objects::isNull)) {
            throw new ApiException(ErrorCode.VALIDATION_FIELD_INVALID, "tagIds must not contain null");
        }
        LinkedHashSet<Integer> unique = new LinkedHashSet<>(tagIds);
        if (unique.isEmpty()) {
            contentItem.getContentItemTags().clear();
            return;
        }

        List<Tag> existing = tagRepository.findAllById(unique);
        if (existing.size() != unique.size()) {
            throw new ApiException(ErrorCode.TAG_NOT_FOUND, "One or more tags were not found");
        }
        List<Integer> inactive = existing.stream()
                .filter(t -> t.getStatus() != TagStatus.ACTIVE)
                .map(Tag::getId)
                .toList();
        if (!inactive.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FIELD_INVALID,
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

    private static Set<Integer> extractTagIds(ContentItem ci) {
        if (ci.getContentItemTags() == null || ci.getContentItemTags().isEmpty()) {
            return Set.of();
        }
        return ci.getContentItemTags().stream()
                .map(cit -> cit.getTag().getId())
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
