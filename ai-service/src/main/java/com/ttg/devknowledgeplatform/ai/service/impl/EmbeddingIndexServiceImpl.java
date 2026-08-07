package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.dto.EmbeddingStatsProjection;
import com.ttg.devknowledgeplatform.ai.dto.admin.EmbeddingIndexItemResponse;
import com.ttg.devknowledgeplatform.ai.dto.client.ContentItemDto;
import com.ttg.devknowledgeplatform.ai.repository.ContentEmbeddingRepository;
import com.ttg.devknowledgeplatform.ai.service.ContentServiceClient;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingIndexService;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of {@link EmbeddingIndexService}.
 *
 * <p>Uses a two-query pattern, same shape as before {@code content-service} was targeted for
 * standalone-service extraction, but the first query now goes over HTTP instead of a local JPA
 * {@code Specification}:
 * <ol>
 *   <li>{@link ContentServiceClient#list} fetches the paginated {@code ContentItem} page.</li>
 *   <li>A single batch JPQL aggregate query fetches embedding stats for all IDs on that page,
 *       from this module's own {@code ContentEmbeddingRepository}.</li>
 * </ol>
 *
 * <p>The {@code indexed} filter can no longer be a cross-service {@code EXISTS} join now that
 * {@code ContentEmbedding} and {@code ContentItem} live in different services' databases. Instead,
 * this class first reads the full set of embedded content item ids from its own database
 * ({@link ContentEmbeddingRepository#findDistinctContentItemIds()} — cheap, one column, no joins),
 * then asks {@code content-service} to intersect (({@code indexed=true}) or exclude
 * ({@code indexed=false})) that id set as part of its own paginated query, so pagination stays
 * correct without an in-memory scan over every matching row.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Throwable.class)
public class EmbeddingIndexServiceImpl implements EmbeddingIndexService {

    private final ContentServiceClient contentServiceClient;
    private final ContentEmbeddingRepository contentEmbeddingRepository;

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<EmbeddingIndexItemResponse> list(
            int page, int size, String q, String contentType, String contentStatus, Boolean indexed) {

        ContentType type = (contentType != null && !contentType.isBlank()) ? ContentType.valueOf(contentType) : null;
        ContentStatus status = (contentStatus != null && !contentStatus.isBlank()) ? ContentStatus.valueOf(contentStatus) : null;

        List<Integer> ids = null;
        List<Integer> excludeIds = null;
        if (indexed != null) {
            List<Integer> embeddedIds = contentEmbeddingRepository.findDistinctContentItemIds();
            if (indexed) {
                ids = embeddedIds;
            } else {
                excludeIds = embeddedIds;
            }
        }

        PagedResponse<ContentItemDto> ciPage = contentServiceClient.list(page, size, q, type, status, ids, excludeIds);

        List<Integer> pageIds = ciPage.getContent().stream().map(ContentItemDto::getId).toList();
        Map<Integer, EmbeddingStatsProjection> statsMap = pageIds.isEmpty() ? Map.of()
                : contentEmbeddingRepository.findStatsByContentItemIds(pageIds).stream()
                        .collect(Collectors.toMap(EmbeddingStatsProjection::getContentItemId, Function.identity()));

        List<EmbeddingIndexItemResponse> content = ciPage.getContent().stream()
                .map(ci -> toResponse(ci, statsMap.get(ci.getId())))
                .toList();

        return PagedResponse.<EmbeddingIndexItemResponse>builder()
                .content(content)
                .totalElements(ciPage.getTotalElements())
                .totalPages(ciPage.getTotalPages())
                .number(ciPage.getNumber())
                .size(ciPage.getSize())
                .build();
    }

    private EmbeddingIndexItemResponse toResponse(ContentItemDto ci, EmbeddingStatsProjection stats) {
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
