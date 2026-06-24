package com.ttg.devknowledgeplatform.ai.filter;

import com.ttg.devknowledgeplatform.ai.dto.ContentEmbeddingMetadata;
import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * {@link RagFilterStrategy} that restricts retrieval to chunks sharing at least one tag
 * with {@link RagFilter#tags()}.
 *
 * <p>Tag names are read from {@link ContentEmbeddingMetadata#tagNames()}, populated at index
 * time by {@code ContentIndexingServiceImpl}. A chunk with a {@code null} or empty tag list
 * is rejected when this filter is active.
 */
@Component
public class MetadataTagFilterStrategy implements RagFilterStrategy {

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code true} if the chunk's stored tag-name list contains any tag present
     * in {@link RagFilter#tags()}.
     */
    @Override
    public Predicate<ContentEmbedding> predicate(RagFilter filter) {
        return ce -> {
            ContentEmbeddingMetadata metadata = ce.getMetadata();
            if (metadata == null || metadata.tagNames() == null) return false;
            return metadata.tagNames().stream().anyMatch(filter.tags()::contains);
        };
    }

    @Override
    public boolean isApplicable(RagFilter filter) {
        return filter.tags() != null && !filter.tags().isEmpty();
    }
}
