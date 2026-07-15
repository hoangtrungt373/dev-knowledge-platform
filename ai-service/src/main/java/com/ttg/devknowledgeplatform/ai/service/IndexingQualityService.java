package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.content.enums.ContentType;

/**
 * Evaluates the quality of a document's embeddings after ingestion by comparing each chunk
 * against the corpus centroid (Option B — centroid distance check).
 *
 * <h3>Why centroid distance at indexing time</h3>
 * <p>The corpus centroid is the normalised average of all indexed embeddings — the geometric
 * centre of the platform's knowledge domain. A document whose chunks land far from this centre
 * is likely off-domain (corrupted OCR, random HTML, accidentally uploaded content). Detecting
 * and recording this at indexing time prevents bad documents from polluting the corpus and
 * degrading retrieval quality for every future query.
 *
 * <h3>Graceful cold-start</h3>
 * <p>If no centroid is available yet (empty corpus or first startup before the first scheduled
 * refresh), the check is skipped and {@link QualityVerdict#skipped()} is returned. The document
 * is indexed normally — the quality score is recorded as {@code null} on the {@code ContentItem},
 * and the check will apply to subsequent documents once the centroid is computed.
 *
 * <h3>Score persistence</h3>
 * <p>Callers are responsible for persisting {@link QualityVerdict#score()} onto
 * {@code ContentItem.qualityScore}. The service only computes the verdict; it does not
 * write to the {@code ContentItem} table.
 */
public interface IndexingQualityService {

    /**
     * Assesses the quality of all embeddings stored for a content item.
     *
     * @param contentItemId the primary key of the content item whose embeddings to assess
     * @param contentType   used to select the most specific corpus centroid available
     * @return a verdict carrying the quality flag and the raw mean centroid similarity score
     */
    QualityVerdict assess(Integer contentItemId, ContentType contentType);
}
