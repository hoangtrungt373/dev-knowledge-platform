-- liquibase formatted sql
-- changeset ttg:202607050001__0.0.1__DKP-0013__add_seed_id_to_category_tag_content_item logicalFilePath:DevKnowledgePlatform
-- comment: Add nullable SEED_ID to CATEGORY, TAG, CONTENT_ITEM for idempotent seed-data loading
--
-- DKP-0013: Add SEED_ID to CATEGORY, TAG, CONTENT_ITEM.
--
-- Design rationale:
--   DataSeedingRunner (api/service/seed/) loads long-lived reference/demo content from
--   classpath CSV/Markdown files that coexist permanently with user-created rows in these same
--   tables (not throwaway dev data — wiped-and-reseeded environments were ruled out). The
--   seeders need a stable "have I already inserted this seed row" check that survives edits to
--   any human-facing field (NAME, TITLE, SLUG), so those fields stay freely editable without
--   risking either (a) a duplicate INSERT on re-run, or (b) reusing NAME/SLUG as the idempotency
--   key and accidentally treating an unrelated same-named row as "already seeded".
--
--   SEED_ID is a natural/surrogate-key split (the standard ETL pattern): the real PK
--   (CATEGORY_ID/TAG_ID/CONTENT_ITEM_ID) stays sequence-generated and is never authored in seed
--   files — hardcoding PK values would desync the backing sequence and collide with the next
--   row a real user creates through the app. SEED_ID is a separate, nullable, seed-only column:
--   NULL for every user/admin-created row, populated only for rows the seeders created, and
--   never surfaced to end users (it plays no role in URLs, filtering, or the API — that's what
--   SLUG/NAME already do). A UNIQUE index (not a NOT NULL constraint) lets every non-seeded row
--   share NULL — Postgres treats NULLs as distinct under a unique index — while still catching
--   an accidental duplicate seed-file identifier.

ALTER TABLE product.CATEGORY
    ADD COLUMN IF NOT EXISTS SEED_ID VARCHAR(100);

ALTER TABLE product.TAG
    ADD COLUMN IF NOT EXISTS SEED_ID VARCHAR(100);

ALTER TABLE product.CONTENT_ITEM
    ADD COLUMN IF NOT EXISTS SEED_ID VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS UX_CATEGORY_SEED_ID ON product.CATEGORY (SEED_ID);
CREATE UNIQUE INDEX IF NOT EXISTS UX_TAG_SEED_ID ON product.TAG (SEED_ID);
CREATE UNIQUE INDEX IF NOT EXISTS UX_CONTENT_ITEM_SEED_ID ON product.CONTENT_ITEM (SEED_ID);
