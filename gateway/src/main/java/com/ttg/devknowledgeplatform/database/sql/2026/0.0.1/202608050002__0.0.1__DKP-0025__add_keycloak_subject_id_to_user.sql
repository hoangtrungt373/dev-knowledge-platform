-- liquibase formatted sql
-- changeset ttg:202608050002__0.0.1__DKP-0025__add_keycloak_subject_id_to_user logicalFilePath:DevKnowledgePlatform
-- comment: Add USER.KEYCLOAK_SUBJECT_ID — the join key for JIT-provisioning a local User row from a Keycloak JWT
--
-- DKP-0025: part of the Keycloak identity-provider migration (see docs/CHANGELOG.md's
-- [Unreleased] entry). A new column, not a reuse of the existing PROVIDER/PROVIDER_ID pair —
-- that pair's semantic (which upstream social IdP, and that IdP's own subject id) is now
-- Keycloak's own internal concern (its FEDERATED_IDENTITY table), not this app's. PROVIDER/
-- PROVIDER_ID/PASSWORD are left in place as inert historical columns, not touched here.
--
-- Nullable, like PROVIDER_ID: existing rows (seeded or otherwise) have no Keycloak account yet
-- and get linked lazily — either by a future login whose JIT-provisioning converter falls back to
-- matching by EMAIL, or by a one-time backfill script. Postgres treats every NULL as distinct, so
-- a unique constraint is safe pre-backfill (same reasoning already documented on
-- UK_USER_PROVIDER_PROVIDER_ID).

ALTER TABLE product.USER
    ADD COLUMN KEYCLOAK_SUBJECT_ID VARCHAR(255);

ALTER TABLE product.USER
    ADD CONSTRAINT UK_USER_KEYCLOAK_SUBJECT_ID UNIQUE (KEYCLOAK_SUBJECT_ID);
