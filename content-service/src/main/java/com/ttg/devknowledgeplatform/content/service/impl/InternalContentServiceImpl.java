package com.ttg.devknowledgeplatform.content.service.impl;

import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import com.ttg.devknowledgeplatform.content.dto.internal.InternalContentItemResponse;
import com.ttg.devknowledgeplatform.content.entity.Article;
import com.ttg.devknowledgeplatform.content.entity.ContentItem;
import com.ttg.devknowledgeplatform.content.entity.QuestionAnswer;
import com.ttg.devknowledgeplatform.content.exception.ContentErrorCode;
import com.ttg.devknowledgeplatform.content.mapper.InternalContentItemMapper;
import com.ttg.devknowledgeplatform.content.repository.ArticleRepository;
import com.ttg.devknowledgeplatform.content.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.content.repository.QuestionAnswerRepository;
import com.ttg.devknowledgeplatform.content.repository.spec.ContentItemSpecification;
import com.ttg.devknowledgeplatform.content.service.InternalContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Implementation of {@link InternalContentService}.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Throwable.class)
public class InternalContentServiceImpl implements InternalContentService {

    private final ContentItemRepository contentItemRepository;
    private final QuestionAnswerRepository questionAnswerRepository;
    private final ArticleRepository articleRepository;
    private final InternalContentItemMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public InternalContentItemResponse getById(Integer id) {
        return toResponse(findById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternalContentItemResponse> listByStatus(ContentStatus status) {
        return contentItemRepository.findByStatus(status).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<InternalContentItemResponse> list(
            int page, int size, String q, ContentType type, ContentStatus status,
            List<Integer> ids, List<Integer> excludeIds) {
        Specification<ContentItem> spec = ContentItemSpecification.withFilters(q, type, status, ids, excludeIds);
        Page<ContentItem> contentItems = contentItemRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        return PagedResponse.from(contentItems.map(this::toResponse));
    }

    @Override
    public void updateQualityScore(Integer id, BigDecimal qualityScore) {
        ContentItem item = findById(id);
        item.setQualityScore(qualityScore);
        contentItemRepository.save(item);
    }

    private ContentItem findById(Integer id) {
        return contentItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ContentErrorCode.CONTENT_ITEM_NOT_FOUND, new Object[] {id}));
    }

    private InternalContentItemResponse toResponse(ContentItem item) {
        QuestionAnswer qa = item.getType() == ContentType.QUESTION_ANSWER
                ? questionAnswerRepository.findByContentItem_Id(item.getId()).orElse(null)
                : null;
        Article article = (item.getType() == ContentType.ARTICLE || item.getType() == ContentType.BLOG_POST)
                ? articleRepository.findByContentItem_Id(item.getId()).orElse(null)
                : null;
        return mapper.toResponse(item, qa, article);
    }
}
