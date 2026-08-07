package com.ttg.devknowledgeplatform.content.dto.internal;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request body for {@code PATCH /internal/content-items/{id}/quality-score} — written by
 * {@code ai-service} immediately after computing a document's corpus-coherence score.
 */
@Data
public class UpdateQualityScoreRequest {

    @NotNull
    private BigDecimal qualityScore;
}
