-- liquibase formatted sql
-- changeset ttg:202606300001__0.0.1__DKP-0011__add_cost_latency_to_pipeline_metrics logicalFilePath:DevKnowledgePlatform
-- comment: Add cost, latency, token-usage, and user-attribution columns to PIPELINE_METRICS

-- =============================================================================
-- PIPELINE_METRICS — cost & latency columns
--
-- Feature 1: Stage latency columns
--   Per-stage wall-clock times extracted from StageSpan records. Individual columns
--   chosen over a JSONB blob because PostgreSQL aggregate functions (percentile_cont,
--   avg, max) work directly on typed columns with no extraction overhead.
--   NULL means the pipeline aborted before reaching that stage.
--
-- Feature 2: Token usage & estimated cost
--   Raw token counts from LangChain4j TokenUsage, separated by model call so that
--   pricing can be re-applied if OpenAI changes rates (the formula is: re-multiply
--   the raw counts — no migration needed).
--   ESTIMATED_COST_USD is DECIMAL(12,8): up to 9999.99999999 USD with 8 decimal places,
--   sufficient for single-request granularity at current pricing.
--   NULL when all token counts are zero (aborted pipeline before any LLM call).
--
-- Feature 3: User attribution
--   USER_ID is deliberately NOT a foreign key. This is an append-only analytics table;
--   deleting a user must not cascade into historical cost records.
-- =============================================================================

ALTER TABLE product.PIPELINE_METRICS
    -- Feature 1: Stage latencies
    ADD COLUMN IF NOT EXISTS CONTEXTUALIZATION_MS             BIGINT,
    ADD COLUMN IF NOT EXISTS EMBEDDING_MS                     BIGINT,
    ADD COLUMN IF NOT EXISTS RETRIEVAL_MS                     BIGINT,
    ADD COLUMN IF NOT EXISTS LLM_GENERATION_MS                BIGINT,
    ADD COLUMN IF NOT EXISTS TOTAL_PIPELINE_MS                BIGINT,

    -- Feature 2: Token usage
    ADD COLUMN IF NOT EXISTS CONTEXTUALIZATION_INPUT_TOKENS   INTEGER,
    ADD COLUMN IF NOT EXISTS CONTEXTUALIZATION_OUTPUT_TOKENS  INTEGER,
    ADD COLUMN IF NOT EXISTS EMBEDDING_TOKENS                 INTEGER,
    ADD COLUMN IF NOT EXISTS QUALITY_EMBEDDING_TOKENS         INTEGER,
    ADD COLUMN IF NOT EXISTS GENERATION_INPUT_TOKENS          INTEGER,
    ADD COLUMN IF NOT EXISTS GENERATION_OUTPUT_TOKENS         INTEGER,
    ADD COLUMN IF NOT EXISTS ESTIMATED_COST_USD               DECIMAL(12, 8),

    -- Feature 3: User attribution
    ADD COLUMN IF NOT EXISTS USER_ID                          INTEGER;

-- Partial index — cost analysis filtered by user.
-- Partial (WHERE USER_ID IS NOT NULL) keeps the index small: anonymous/internal
-- calls are excluded from user-level budget reports, so they do not need to be indexed.
CREATE INDEX IF NOT EXISTS IDX_PIPELINE_METRICS_USER_ID
    ON product.PIPELINE_METRICS (USER_ID)
    WHERE USER_ID IS NOT NULL;
