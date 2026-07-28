-- liquibase formatted sql
-- changeset ttg:202607270002__0.0.1__DKP-0022__remove_content_item_id_from_task logicalFilePath:DevKnowledgePlatform
-- comment: Drop TASK.CONTENT_ITEM_ID (Task -> content-service ContentItem link) — never used by any client
--
-- DKP-0022: removes the optional Task -> ContentItem link added by DKP-0020
-- (CONTENT_ITEM_ID/FK_TASK_CONTENT_ITEM/IDX_TASK_CONTENT_ITEM) and never wired up on the GUI
-- side (no content-item picker was ever built). task-service no longer depends on
-- content-service as of this change (see task-service/CLAUDE.md).
--
-- Split into its own changeset rather than folded back into DKP-0020 — that changeset had
-- already executed against a real DB by the time this need came up; editing it in place would
-- cause a Liquibase checksum-mismatch failure on the next `update` (same reason DKP-0021 exists
-- as its own changeset instead of being folded into DKP-0020).

DROP INDEX IF EXISTS product.IDX_TASK_CONTENT_ITEM;

ALTER TABLE product.TASK
    DROP CONSTRAINT IF EXISTS FK_TASK_CONTENT_ITEM;

ALTER TABLE product.TASK
    DROP COLUMN IF EXISTS CONTENT_ITEM_ID;
