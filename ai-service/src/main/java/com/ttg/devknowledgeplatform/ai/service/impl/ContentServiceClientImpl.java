package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.config.ContentServiceClientProperties;
import com.ttg.devknowledgeplatform.ai.dto.client.ContentItemDto;
import com.ttg.devknowledgeplatform.ai.service.ContentServiceClient;
import com.ttg.devknowledgeplatform.common.dto.PagedResponse;
import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.common.exception.ResourceNotFoundException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link ContentServiceClient}, backed by a Spring {@link RestClient}.
 */
@Service
public class ContentServiceClientImpl implements ContentServiceClient {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;

    public ContentServiceClientImpl(ContentServiceClientProperties properties, RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(API_KEY_HEADER, properties.getInternalApiKey())
                // Stamps traceparent per-call from whatever's on the current thread's MDC right
                // now — a defaultHeader() can't do this, since it would freeze whatever trace
                // context existed when this singleton bean was constructed at startup, long
                // before any real request's trace exists. See the interceptor's own Javadoc for
                // the known async-pipeline gap.
                .requestInterceptor(new TraceparentClientHttpRequestInterceptor())
                .build();
    }

    @Override
    public ContentItemDto getById(Integer id) {
        try {
            return restClient.get()
                    .uri("/internal/content-items/{id}", id)
                    .retrieve()
                    .body(ContentItemDto.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("ContentItem", String.valueOf(id));
        }
    }

    @Override
    public List<ContentItemDto> listByStatus(ContentStatus status) {
        return restClient.get()
                .uri("/internal/content-items/by-status/{status}", status)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ContentItemDto>>() {});
    }

    @Override
    public PagedResponse<ContentItemDto> list(
            int page, int size, String q, ContentType type, ContentStatus status,
            List<Integer> ids, List<Integer> excludeIds) {
        return restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/internal/content-items")
                            .queryParam("page", page)
                            .queryParam("size", size);
                    if (q != null && !q.isBlank()) {
                        uriBuilder.queryParam("q", q);
                    }
                    if (type != null) {
                        uriBuilder.queryParam("type", type);
                    }
                    if (status != null) {
                        uriBuilder.queryParam("status", status);
                    }
                    if (ids != null && !ids.isEmpty()) {
                        uriBuilder.queryParam("ids", ids);
                    }
                    if (excludeIds != null && !excludeIds.isEmpty()) {
                        uriBuilder.queryParam("excludeIds", excludeIds);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<PagedResponse<ContentItemDto>>() {});
    }

    @Override
    public void updateQualityScore(Integer id, BigDecimal qualityScore) {
        restClient.patch()
                .uri("/internal/content-items/{id}/quality-score", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("qualityScore", qualityScore))
                .retrieve()
                .toBodilessEntity();
    }
}
