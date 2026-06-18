package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import com.ttg.devknowledgeplatform.ai.repository.ContentEmbeddingRepository;
import com.ttg.devknowledgeplatform.ai.service.ContentIngestionService;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingService;
import com.ttg.devknowledgeplatform.ai.service.TextChunkingService;
import com.ttg.devknowledgeplatform.common.entity.ContentItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContentIngestionServiceImpl implements ContentIngestionService {

    private final TextChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final ContentEmbeddingRepository repository;
    private final EmbeddingProperties properties;

    @Override
    public void ingest(ContentItem contentItem, String fullText, Map<String, Object> extraMetadata) {
        Integer contentItemId = contentItem.getId();
        log.info("Ingesting content item id={} type={}", contentItemId, contentItem.getType());

        repository.deleteByContentItem_IdAndModelName(contentItemId, properties.getModel());

        List<String> chunks = chunkingService.chunk(fullText);
        if (chunks.isEmpty()) {
            log.warn("No chunks produced for content item id={} — skipping", contentItemId);
            return;
        }

        List<float[]> embeddings = embeddingService.embedBatch(chunks);

        Map<String, Object> baseMetadata = buildBaseMetadata(contentItem);
        baseMetadata.putAll(extraMetadata);

        List<ContentEmbedding> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            ContentEmbedding ce = new ContentEmbedding();
            ce.setContentItem(contentItem);
            ce.setSourceType(contentItem.getType());
            ce.setChunkIndex(i);
            ce.setChunkText(chunk);
            ce.setEmbedding(embeddings.get(i));
            ce.setModelName(properties.getModel());
            ce.setDimensions(properties.getDimensions());
            ce.setTokenCount(chunkingService.estimateTokens(chunk));
            ce.setMetadata(new HashMap<>(baseMetadata));
            rows.add(ce);
        }

        repository.saveAll(rows);
        log.info("Stored {} embeddings for content item id={} model={}",
                rows.size(), contentItemId, properties.getModel());
    }

    @Override
    public void deleteEmbeddings(Integer contentItemId) {
        repository.deleteByContentItem_Id(contentItemId);
        log.info("Deleted all embeddings for content item id={}", contentItemId);
    }

    private Map<String, Object> buildBaseMetadata(ContentItem contentItem) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", contentItem.getType().name());
        metadata.put("status", contentItem.getStatus().name());
        metadata.put("title", contentItem.getTitle());
        if (contentItem.getCategory() != null) {
            metadata.put("categoryId", contentItem.getCategory().getId());
            metadata.put("categoryName", contentItem.getCategory().getName());
        }
        return metadata;
    }
}
