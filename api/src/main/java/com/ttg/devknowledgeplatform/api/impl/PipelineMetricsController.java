package com.ttg.devknowledgeplatform.api.impl;

import com.ttg.devknowledgeplatform.ai.dto.MetricsPeriod;
import com.ttg.devknowledgeplatform.ai.dto.PipelineMetricsSummary;
import com.ttg.devknowledgeplatform.ai.service.PipelineMetricsSummaryService;
import com.ttg.devknowledgeplatform.api.PipelineMetricsApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementation of {@link PipelineMetricsApi}.
 *
 * <p>Contains no HTTP annotations — those live entirely on {@link PipelineMetricsApi}.
 * Delegates directly to {@link PipelineMetricsSummaryService}; no mapping logic belongs
 * here (the service already returns the API response record).
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class PipelineMetricsController implements PipelineMetricsApi {

    private final PipelineMetricsSummaryService summaryService;

    @Override
    public ResponseEntity<PipelineMetricsSummary> getSummary(MetricsPeriod period) {
        log.info("Pipeline metrics summary requested: period={}", period);
        return ResponseEntity.ok(summaryService.getSummary(period));
    }
}
