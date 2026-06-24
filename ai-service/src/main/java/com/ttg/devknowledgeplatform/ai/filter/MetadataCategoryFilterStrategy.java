package com.ttg.devknowledgeplatform.ai.filter;

import com.ttg.devknowledgeplatform.ai.dto.ContentEmbeddingMetadata;
import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * {@link RagFilterStrategy} that restricts retrieval to chunks belonging to a specific category.
 *
 * <p>The category identifier is read from {@link ContentEmbeddingMetadata#categoryId()},
 * populated at index time by {@code ContentIndexingServiceImpl}. A chunk with a {@code null}
 * category is rejected when this filter is active.
 */
@Component
public class MetadataCategoryFilterStrategy implements RagFilterStrategy {

    @Override
    public Predicate<ContentEmbedding> predicate(RagFilter filter) {
        return ce -> {
            ContentEmbeddingMetadata metadata = ce.getMetadata();
            if (metadata == null) return false;
            return filter.categoryId().equals(metadata.categoryId());
        };
    }

    @Override
    public boolean isApplicable(RagFilter filter) {
        return filter.categoryId() != null;
    }
}
