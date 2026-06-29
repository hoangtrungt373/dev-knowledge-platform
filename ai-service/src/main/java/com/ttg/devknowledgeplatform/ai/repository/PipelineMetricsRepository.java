package com.ttg.devknowledgeplatform.ai.repository;

import com.ttg.devknowledgeplatform.ai.entity.PipelineMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link PipelineMetrics} analytics rows.
 *
 * <p>Writes are the primary operation (one INSERT per RAG request). Reads are
 * ad-hoc SQL queries for threshold tuning; no derived query methods are declared
 * here — use a direct DB client for those.
 */
public interface PipelineMetricsRepository extends JpaRepository<PipelineMetrics, Integer> {
}
