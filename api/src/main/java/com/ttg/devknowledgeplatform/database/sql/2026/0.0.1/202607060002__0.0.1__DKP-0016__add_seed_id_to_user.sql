-- liquibase formatted sql
-- changeset ttg:202607060002__0.0.1__DKP-0016__add_seed_id_to_user logicalFilePath:DevKnowledgePlatform
-- comment: Add nullable SEED_ID to USER for idempotent sample-data loading
--
-- DKP-0016: Add SEED_ID to USER.
--
-- Design rationale: same pattern as DKP-0013 (CATEGORY/TAG/CONTENT_ITEM). UserSeeder
-- (api/service/seed/) loads long-lived sample accounts from a classpath CSV that coexist
-- permanently with real user-created rows in USER. EMAIL/USERNAME are human-editable
-- (UserServiceImpl.updateProfile) the same way CATEGORY.NAME/TAG.NAME are, so they can't be the
-- idempotency check without risking a duplicate INSERT if a seeded user's email/username is ever
-- edited post-seeding. SEED_ID is a separate, nullable, seed-only column — NULL for every
-- real/admin-created row, populated only for seeded rows, never surfaced to end users.
--
-- FRIEND_REQUEST/FRIENDSHIP/USER_BLOCK deliberately do NOT get their own SEED_ID: their identity
-- is the (user, user) pair itself, which has no editable-field equivalent to NAME/EMAIL that
-- could invalidate a pair-based idempotency check — "does a row already exist for this pair,
-- referencing users by their permanent USER.SEED_ID" is stable on its own, the same way
-- CONTENT_ITEM_TAG never needed its own SEED_ID.

ALTER TABLE product.USER
    ADD COLUMN IF NOT EXISTS SEED_ID VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS UX_USER_SEED_ID ON product.USER (SEED_ID);
