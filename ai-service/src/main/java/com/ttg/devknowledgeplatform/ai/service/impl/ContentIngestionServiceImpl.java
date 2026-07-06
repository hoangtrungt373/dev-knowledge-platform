package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.config.ModelConfig;
import com.ttg.devknowledgeplatform.ai.dto.ContentEmbeddingMetadata;
import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import com.ttg.devknowledgeplatform.ai.repository.ContentEmbeddingRepository;
import com.ttg.devknowledgeplatform.ai.service.ContentIngestionService;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingService;
import com.ttg.devknowledgeplatform.ai.service.TextChunkingService;
import com.ttg.devknowledgeplatform.content.entity.ContentItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Default {@link ContentIngestionService} implementation.
 *
 * <p>Receives a pre-built {@link ContentEmbeddingMetadata} from the caller rather than
 * constructing it internally. This keeps the metadata schema contract in one place
 * ({@code ContentIndexingServiceImpl}) and avoids the previous two-step merge where
 * both this class and its caller wrote overlapping keys into separate maps.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ContentIngestionServiceImpl implements ContentIngestionService {

    private final TextChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final ContentEmbeddingRepository repository;
    private final ModelConfig model;

    @Override
    public void ingest(ContentItem contentItem, String fullText, ContentEmbeddingMetadata metadata) {
        Integer contentItemId = contentItem.getId();
        log.info("Ingesting content item id={} type={}", contentItemId, contentItem.getType());

        repository.deleteByContentItem_IdAndModelName(contentItemId, model.getModel());

        List<String> chunks = chunkingService.chunk(fullText);
        if (chunks.isEmpty()) {
            log.warn("No chunks produced for content item id={} — skipping", contentItemId);
            return;
        }

        List<float[]> embeddings = embeddingService.embedBatch(chunks);

        List<ContentEmbedding> rows = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            ContentEmbedding ce = new ContentEmbedding();
            ce.setContentItem(contentItem);
            ce.setSourceType(contentItem.getType());
            ce.setChunkIndex(i);
            ce.setChunkText(chunk);
            ce.setEmbedding(embeddings.get(i));
            ce.setModelName(model.getModel());
            ce.setDimensions(model.getDimensions());
            ce.setTokenCount(chunkingService.estimateTokens(chunk));
            ce.setMetadata(metadata);
            rows.add(ce);
        }

        repository.saveAll(rows);
        log.info("Stored {} embeddings for content item id={} model={}",
                rows.size(), contentItemId, model.getModel());
    }

    @Override
    public void deleteEmbeddings(Integer contentItemId) {
        repository.deleteByContentItem_Id(contentItemId);
        log.info("Deleted all embeddings for content item id={}", contentItemId);
    }
}
