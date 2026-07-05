package com.ttg.devknowledgeplatform.service.impl;

import com.ttg.devknowledgeplatform.ai.dto.EmbeddingStatsProjection;
import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import com.ttg.devknowledgeplatform.ai.repository.ContentEmbeddingRepository;
import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.EmbeddingIndexItemResponse;
import com.ttg.devknowledgeplatform.repository.ContentItemRepository;
import com.ttg.devknowledgeplatform.service.EmbeddingIndexService;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of {@link EmbeddingIndexService}.
 *
 * <p>Uses a two-query pattern:
 * <ol>
 *   <li>A paginated JPA Specification query fetches the {@code ContentItem} page.</li>
 *   <li>A single batch JPQL aggregate query fetches embedding stats for all IDs on that page.</li>
 * </ol>
 * This avoids N+1 queries while keeping the embedding aggregate logic in the
 * {@code ai-service} module where it belongs.
 *
 * <p>The {@code indexed} filter uses a JPA Criteria {@code EXISTS} subquery
 * on {@code ContentEmbedding} rather than a join, so the pagination count query
 * remains accurate and unaffected by the one-to-many relationship.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Throwable.class)
public class EmbeddingIndexServiceImpl implements EmbeddingIndexService {

    private final ContentItemRepository contentItemRepository;
    private final ContentEmbeddingRepository contentEmbeddingRepository;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<EmbeddingIndexItemResponse> list(
            int page, int size, String q, String contentType, String contentStatus, Boolean indexed) {

        Specification<ContentItem> spec = buildSpec(q, contentType, contentStatus, indexed);
        Page<ContentItem> ciPage = contentItemRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));

        List<Integer> ids = ciPage.getContent().stream().map(ContentItem::getId).toList();
        Map<Integer, EmbeddingStatsProjection> statsMap = ids.isEmpty() ? Map.of()
                : contentEmbeddingRepository.findStatsByContentItemIds(ids).stream()
                        .collect(Collectors.toMap(EmbeddingStatsProjection::getContentItemId, Function.identity()));

        return PagedResponse.from(ciPage.map(ci -> toResponse(ci, statsMap.get(ci.getId()))));
    }

    private Specification<ContentItem> buildSpec(
            String q, String contentType, String contentStatus, Boolean indexed) {

        Specification<ContentItem> spec = Specification.where(null);

        if (q != null && !q.isBlank()) {
            String pattern = "%" + q.toLowerCase() + "%";
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("title")), pattern));
        }
        if (contentType != null && !contentType.isBlank()) {
            ContentType type = ContentType.valueOf(contentType);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (contentStatus != null && !contentStatus.isBlank()) {
            ContentStatus status = ContentStatus.valueOf(contentStatus);
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (indexed != null) {
            spec = spec.and((root, query, cb) -> {
                // EXISTS subquery: checks whether any ContentEmbedding row references this ContentItem.
                // Avoids inflating the row count that a JOIN would cause with the one-to-many relation.
                Subquery<ContentEmbedding> sub = query.subquery(ContentEmbedding.class);
                Root<ContentEmbedding> ceRoot = sub.from(ContentEmbedding.class);
                sub.select(ceRoot).where(cb.equal(ceRoot.get("contentItem"), root));
                return indexed ? cb.exists(sub) : cb.not(cb.exists(sub));
            });
        }
        return spec;
    }

    private EmbeddingIndexItemResponse toResponse(ContentItem ci, EmbeddingStatsProjection stats) {
        return EmbeddingIndexItemResponse.builder()
                .contentItemId(ci.getId())
                .title(ci.getTitle())
                .contentType(ci.getType().name())
                .contentStatus(ci.getStatus().name())
                .qualityScore(ci.getQualityScore() != null ? ci.getQualityScore().doubleValue() : null)
                .chunkCount(stats != null ? stats.getChunkCount() : 0L)
                .totalTokens(stats != null ? stats.getTotalTokens() : 0L)
                .modelName(stats != null ? stats.getModelName() : null)
                .lastIndexedAt(stats != null ? stats.getLastIndexedAt() : null)
                .indexed(stats != null && stats.getChunkCount() != null && stats.getChunkCount() > 0)
                .build();
    }
}
