-- liquibase formatted sql
-- changeset ttg:202606290001__0.0.1__DKP-0010__create_pipeline_metrics_table logicalFilePath:DevKnowledgePlatform
-- comment: Create PIPELINE_METRICS table for per-request RAG quality analytics

-- =============================================================================
-- PIPELINE_METRICS
-- Append-only analytics table. One row per RAG pipeline execution.
-- All columns after CREATED_AT are nullable: a NULL value means the corresponding
-- stage did not execute because the pipeline was aborted earlier.
--
-- Primary use case: data-driven threshold tuning. Example queries:
--
--   -- Evidence mean score distribution on successful requests
--   SELECT percentile_cont(0.10) WITHIN GROUP (ORDER BY EVIDENCE_MEAN_SCORE) AS p10,
--          percentile_cont(0.50) WITHIN GROUP (ORDER BY EVIDENCE_MEAN_SCORE) AS p50,
--          percentile_cont(0.95) WITHIN GROUP (ORDER BY EVIDENCE_MEAN_SCORE) AS p95
--   FROM product.PIPELINE_METRICS WHERE ABORTED_AT IS NULL;
--
--   -- Guard firing rates
--   SELECT ABORTED_AT, COUNT(*) AS aborts
--   FROM product.PIPELINE_METRICS WHERE ABORTED_AT IS NOT NULL
--   GROUP BY ABORTED_AT ORDER BY aborts DESC;
--
-- Design notes:
--   - No audit columns (USR_CREATION etc.): this is machine-generated analytics, not
--     user-managed content. CREATED_AT alone serves the time-series axis.
--   - No VERSION column: the table is append-only; optimistic locking has no meaning.
--   - DECIMAL(5,4) gives four decimal places of precision for similarity scores in [0,1].
--   - Index on CREATED_AT supports time-range queries in monitoring dashboards.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.PIPELINE_METRICS_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.PIPELINE_METRICS (
    PIPELINE_METRICS_ID     INTEGER                         NOT NULL,
    TRACE_ID                VARCHAR(36)                     NOT NULL,
    CREATED_AT              TIMESTAMP WITH TIME ZONE        NOT NULL,
    ABORTED_AT              VARCHAR(100),
    CANDIDATE_COUNT         INTEGER,
    AFTER_SCORING_COUNT     INTEGER,
    SELECTED_COUNT          INTEGER,
    EVIDENCE_MEAN_SCORE     DECIMAL(5, 4),
    EFFECTIVE_SIM_THRESHOLD DECIMAL(5, 4),
    ANSWER_CONTEXT_SIM      DECIMAL(5, 4),
    ANSWER_QUERY_SIM        DECIMAL(5, 4),
    ANSWER_DRIFTED          BOOLEAN,

    CONSTRAINT PK_PIPELINE_METRICS PRIMARY KEY (PIPELINE_METRICS_ID)
);

ALTER SEQUENCE product.PIPELINE_METRICS_SEQ OWNED BY product.PIPELINE_METRICS.PIPELINE_METRICS_ID;

-- Time-range queries (dashboards, trend analysis)
CREATE INDEX IF NOT EXISTS IDX_PIPELINE_METRICS_CREATED_AT
    ON product.PIPELINE_METRICS (CREATED_AT DESC);

-- Filter by outcome (guard firing analysis)
CREATE INDEX IF NOT EXISTS IDX_PIPELINE_METRICS_ABORTED_AT
    ON product.PIPELINE_METRICS (ABORTED_AT)
    WHERE ABORTED_AT IS NOT NULL;
