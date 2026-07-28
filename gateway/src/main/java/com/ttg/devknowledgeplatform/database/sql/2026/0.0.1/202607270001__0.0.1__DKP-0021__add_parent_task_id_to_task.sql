-- liquibase formatted sql
-- changeset ttg:202607270001__0.0.1__DKP-0021__add_parent_task_id_to_task logicalFilePath:DevKnowledgePlatform
-- comment: Add PARENT_TASK_ID self-FK to TASK for one-level-deep subtasks
--
-- DKP-0021: TASK gains an optional self-referential PARENT_TASK_ID, mirroring CATEGORY's
-- parent/child shape (DKP-0002) — a Task can have subtasks, capped at one level deep
-- (TaskErrorCode.TASK_INVALID_PARENT enforces this application-side; the FK only enforces
-- that PARENT_TASK_ID references a real TASK row, not the depth cap).
--
-- Split into its own changeset rather than folded into DKP-0020 (which already executed
-- against dev DBs by the time this need came up) — editing an already-run changeset causes
-- a Liquibase checksum-mismatch failure on next `update`, so this repo's convention going
-- forward is: any further PROJECT/TASK schema change gets its own new changeset.

ALTER TABLE product.TASK
    ADD COLUMN IF NOT EXISTS PARENT_TASK_ID INTEGER;

-- Postgres has no ADD CONSTRAINT IF NOT EXISTS — this changeset only ever runs once per DB
-- (Liquibase tracks that), so plain ADD CONSTRAINT is correct here.
ALTER TABLE product.TASK
    ADD CONSTRAINT FK_TASK_PARENT FOREIGN KEY (PARENT_TASK_ID) REFERENCES product.TASK (TASK_ID);

CREATE INDEX IF NOT EXISTS IDX_TASK_PARENT ON product.TASK (PARENT_TASK_ID);
