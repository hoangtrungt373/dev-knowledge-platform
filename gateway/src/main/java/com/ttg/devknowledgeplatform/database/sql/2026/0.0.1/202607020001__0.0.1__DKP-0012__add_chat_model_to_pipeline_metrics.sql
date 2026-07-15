-- liquibase formatted sql
-- changeset ttg:202607020001__0.0.1__DKP-0012__add_chat_model_to_pipeline_metrics logicalFilePath:DevKnowledgePlatform
-- comment: Add CHAT_MODEL column to PIPELINE_METRICS for per-model cost/latency attribution

-- =============================================================================
-- PIPELINE_METRICS — chat model attribution
--
-- Requests can now be served by any configured chat model (see ChatModelsConfig /
-- ChatModelResolver), not just a single fixed one. CHAT_MODEL stores the id of the profile
-- actually resolved for the request (e.g. "gpt-5.4-mini", "claude-sonnet-5"), so the
-- pipeline-metrics summary endpoint can break down cost and latency per model.
--
-- Nullable: rows written before this column existed have no model attribution and stay NULL
-- rather than being backfilled with a guess.
-- =============================================================================

ALTER TABLE product.PIPELINE_METRICS
    ADD COLUMN IF NOT EXISTS CHAT_MODEL VARCHAR(100);

-- Supports GROUP BY CHAT_MODEL cost/latency breakdowns in the admin summary endpoint.
-- Partial (WHERE CHAT_MODEL IS NOT NULL) keeps the index small — pre-migration rows are excluded.
CREATE INDEX IF NOT EXISTS IDX_PIPELINE_METRICS_CHAT_MODEL
    ON product.PIPELINE_METRICS (CHAT_MODEL)
    WHERE CHAT_MODEL IS NOT NULL;
