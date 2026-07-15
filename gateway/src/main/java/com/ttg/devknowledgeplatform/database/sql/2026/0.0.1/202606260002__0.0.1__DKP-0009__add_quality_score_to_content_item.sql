-- liquibase formatted sql
-- changeset ttg:202606260002__0.0.1__DKP-0009__add_quality_score_to_content_item logicalFilePath:DevKnowledgePlatform
-- comment: Add QUALITY_SCORE column to CONTENT_ITEM for indexing quality tracking
--
-- DKP-0009: Add QUALITY_SCORE column to CONTENT_ITEM.
--
-- QUALITY_SCORE stores the mean cosine similarity between a document's chunk embeddings
-- and the corpus centroid, computed by IndexingQualityServiceImpl after ingestion.
--
-- Design rationale:
--   A nullable DECIMAL column is preferred over adding a flag value to the STATUS column
--   because STATUS models the content lifecycle (DRAFT → PUBLISHED → ARCHIVED) while quality
--   is an orthogonal concern. A float score is preferred over a boolean flag because it
--   preserves the raw signal — admins can query "score < 0.40" directly and the threshold
--   can be changed in config without a migration.
--
-- NULL   = not yet assessed (pre-existing content, or cold-start with no corpus centroid)
-- 0.0–1.0 = assessed; values below app.ai.indexing.indexing-coherence-threshold are low quality

ALTER TABLE product.CONTENT_ITEM
    ADD COLUMN IF NOT EXISTS QUALITY_SCORE DECIMAL(5, 4);
