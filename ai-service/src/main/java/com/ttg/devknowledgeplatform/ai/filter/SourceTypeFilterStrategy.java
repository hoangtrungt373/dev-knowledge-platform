package com.ttg.devknowledgeplatform.ai.filter;

import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

/**
 * {@link RagFilterStrategy} that restricts retrieval to chunks whose
 * {@link ContentEmbedding#getSourceType()} is contained in {@link RagFilter#sourceTypes()}.
 *
 * <p>This filter operates on a structured column rather than the JSONB metadata blob,
 * so the match is exact and type-safe.
 */
@Component
public class SourceTypeFilterStrategy implements RagFilterStrategy {

    @Override
    public Predicate<ContentEmbedding> predicate(RagFilter filter) {
        return ce -> filter.sourceTypes().contains(ce.getSourceType());
    }

    @Override
    public boolean isApplicable(RagFilter filter) {
        return filter.sourceTypes() != null && !filter.sourceTypes().isEmpty();
    }
}
