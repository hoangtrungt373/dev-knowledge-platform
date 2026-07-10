-- liquibase formatted sql
-- changeset ttg:202607080001__0.0.1__DKP-0018__add_missing_unique_constraints_and_indexes logicalFilePath:DevKnowledgePlatform
-- comment: Add missing UNIQUE constraints/indexes and a couple of plain indexes found in a unique-vs-plain index audit
--
-- Several columns were already relied on as unique by service-layer existsBy/findBy code
-- (singular Optional finders, get-or-create patterns) with nothing in the schema actually
-- enforcing it, and a few hot-path lookups had no index at all.

-- =============================================================================
-- USER — EMAIL / USERNAME / (PROVIDER, PROVIDER_ID)
--
-- EMAIL and USERNAME back singular-returning finders (findByEmail, findByUsername in
-- UserRepository) used across login, OAuth linking, and current-user resolution. A duplicate row
-- wouldn't just be "an extra row" — Spring Data would throw IncorrectResultSizeDataAccessException
-- the next time either finder ran, crashing login for the affected accounts. existsByEmail/
-- existsByUsername in UserServiceImpl close the common case but not the concurrent-registration
-- race (two signups with the same email landing between the check and the insert).
--
-- (PROVIDER, PROVIDER_ID) had no index at all despite being looked up on every OAuth/OIDC login
-- (CustomOAuth2UserService, CustomOidcUserService) — a sequential scan on every social login. It's
-- also the natural identity key: one external account must map to at most one local user. No
-- partial WHERE is needed even though PROVIDER_ID is nullable for LOCAL accounts — a unique index
-- treats each NULL as distinct from every other NULL, so any number of LOCAL rows can coexist.
-- =============================================================================

DROP INDEX IF EXISTS product.IDX_USER_EMAIL;
CREATE UNIQUE INDEX IF NOT EXISTS UX_USER_EMAIL ON product.USER (EMAIL);
ALTER TABLE product.USER ADD CONSTRAINT UK_USER_EMAIL UNIQUE USING INDEX UX_USER_EMAIL;

DROP INDEX IF EXISTS product.IDX_USER_USERNAME;
CREATE UNIQUE INDEX IF NOT EXISTS UX_USER_USERNAME ON product.USER (USERNAME);
ALTER TABLE product.USER ADD CONSTRAINT UK_USER_USERNAME UNIQUE USING INDEX UX_USER_USERNAME;

CREATE UNIQUE INDEX IF NOT EXISTS UX_USER_PROVIDER_PROVIDER_ID ON product.USER (PROVIDER, PROVIDER_ID);
ALTER TABLE product.USER
    ADD CONSTRAINT UK_USER_PROVIDER_PROVIDER_ID UNIQUE USING INDEX UX_USER_PROVIDER_PROVIDER_ID;

-- =============================================================================
-- CATEGORY / TAG / CONTENT_ITEM — SLUG
--
-- findBySlug (CategoryRepository, TagRepository, ContentItemRepository) returns a singular
-- Optional and backs the public routing lookup (PublicContentController). SlugService's
-- existsBySlug pre-check (called from every create/rename path) narrows the collision window but
-- doesn't close it under concurrent publishes with colliding titles.
-- =============================================================================

DROP INDEX IF EXISTS product.IDX_CATEGORY_SLUG;
CREATE UNIQUE INDEX IF NOT EXISTS UX_CATEGORY_SLUG ON product.CATEGORY (SLUG);
ALTER TABLE product.CATEGORY ADD CONSTRAINT UK_CATEGORY_SLUG UNIQUE USING INDEX UX_CATEGORY_SLUG;

DROP INDEX IF EXISTS product.IDX_TAG_SLUG;
CREATE UNIQUE INDEX IF NOT EXISTS UX_TAG_SLUG ON product.TAG (SLUG);
ALTER TABLE product.TAG ADD CONSTRAINT UK_TAG_SLUG UNIQUE USING INDEX UX_TAG_SLUG;

DROP INDEX IF EXISTS product.IDX_CONTENT_ITEM_SLUG;
CREATE UNIQUE INDEX IF NOT EXISTS UX_CONTENT_ITEM_SLUG ON product.CONTENT_ITEM (SLUG);
ALTER TABLE product.CONTENT_ITEM ADD CONSTRAINT UK_CONTENT_ITEM_SLUG UNIQUE USING INDEX UX_CONTENT_ITEM_SLUG;

-- =============================================================================
-- CATEGORY / TAG — NAME (case-insensitive)
--
-- existsByNameIgnoreCase / existsByNameIgnoreCaseAndIdNot are the actual dedup check these
-- services rely on (CategoryServiceImpl, TagServiceImpl, both seeders) — case-insensitive. A plain
-- UNIQUE(NAME) wouldn't match that semantic (it would let "Java" and "java" both through), so this
-- is a functional index on LOWER(NAME) instead of the raw column.
--
-- Left as an index, not promoted to a named UNIQUE constraint: Postgres's
-- "ADD CONSTRAINT ... UNIQUE USING INDEX" requires a plain-column index, not an expression index —
-- the index alone enforces the invariant just as well, it just won't show up in
-- information_schema.table_constraints. (See UX_CATEGORY_SLUG above for what the plain-column,
-- constraint-backed version looks like.)
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS UX_CATEGORY_NAME_LOWER ON product.CATEGORY (LOWER(NAME));
CREATE UNIQUE INDEX IF NOT EXISTS UX_TAG_NAME_LOWER ON product.TAG (LOWER(NAME));

-- =============================================================================
-- CONTENT_ITEM — AUTHOR_ID
--
-- Every other reference column on this table (CATEGORY_ID) has an index; AUTHOR_ID has neither an
-- index nor an FK. No "content by author" query exists yet, so this is precautionary rather than a
-- measured hot path — cheap to add now, easy to miss later once that query does exist. The FK is
-- deliberately left out of scope here: adding it requires validating existing AUTHOR_ID values
-- against USER first, which is a separate decision from the index/uniqueness audit this migration
-- covers.
-- =============================================================================

CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_AUTHOR ON product.CONTENT_ITEM (AUTHOR_ID);

-- =============================================================================
-- ARTICLE / QUESTION_ANSWER — CONTENT_ITEM_ID
--
-- Both are 1-row-per-CONTENT_ITEM extension tables (joined-subtype pattern). findByContentItem_Id
-- / existsByContentItemId (ArticleRepository, QuestionAnswerRepository) assume at most one row per
-- content item; nothing in the schema enforced that — two ARTICLE rows could point at the same
-- CONTENT_ITEM_ID with no error.
-- =============================================================================

DROP INDEX IF EXISTS product.IDX_ARTICLE_CONTENT_ITEM;
CREATE UNIQUE INDEX IF NOT EXISTS UX_ARTICLE_CONTENT_ITEM ON product.ARTICLE (CONTENT_ITEM_ID);
ALTER TABLE product.ARTICLE
    ADD CONSTRAINT UK_ARTICLE_CONTENT_ITEM UNIQUE USING INDEX UX_ARTICLE_CONTENT_ITEM;

DROP INDEX IF EXISTS product.IDX_QUESTION_ANSWER_CONTENT;
CREATE UNIQUE INDEX IF NOT EXISTS UX_QUESTION_ANSWER_CONTENT ON product.QUESTION_ANSWER (CONTENT_ITEM_ID);
ALTER TABLE product.QUESTION_ANSWER
    ADD CONSTRAINT UK_QUESTION_ANSWER_CONTENT UNIQUE USING INDEX UX_QUESTION_ANSWER_CONTENT;

-- =============================================================================
-- CONTENT_ITEM_TAG — (CONTENT_ITEM_ID, TAG_ID)
--
-- Many-to-many join table with no pair-uniqueness at all — nothing stopped the same tag being
-- attached to the same content item twice. FRIENDSHIP / USER_BLOCK (DKP-0015) already established
-- this exact pattern for join-style pair tables; this earlier table never got it.
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS UX_CONTENT_ITEM_TAG_PAIR ON product.CONTENT_ITEM_TAG (CONTENT_ITEM_ID, TAG_ID);
ALTER TABLE product.CONTENT_ITEM_TAG
    ADD CONSTRAINT UK_CONTENT_ITEM_TAG_PAIR UNIQUE USING INDEX UX_CONTENT_ITEM_TAG_PAIR;

-- =============================================================================
-- CHAT_MESSAGE — (CHAT_SESSION_ID, TURN_INDEX)
--
-- TURN_INDEX is documented (DKP-0006) as a monotonically increasing counter scoped to the session;
-- two messages sharing a turn number in one session is a bug signal, not a valid state. This
-- replaces the existing plain composite index with an equivalent unique one — same ordering
-- support for "fetch last N messages", plus the invariant now enforced.
-- =============================================================================

DROP INDEX IF EXISTS product.IDX_CHAT_MESSAGE_TURN_ORDER;
CREATE UNIQUE INDEX IF NOT EXISTS UX_CHAT_MESSAGE_TURN_ORDER ON product.CHAT_MESSAGE (CHAT_SESSION_ID, TURN_INDEX);
ALTER TABLE product.CHAT_MESSAGE
    ADD CONSTRAINT UK_CHAT_MESSAGE_TURN_ORDER UNIQUE USING INDEX UX_CHAT_MESSAGE_TURN_ORDER;

-- =============================================================================
-- SYS_PARAM — NAME
--
-- The key of a key-value store, with no index or constraint at all. SysParamServiceImpl's
-- findByName(key).orElseGet(SysParam::new) get-or-create pattern is exactly the race a unique
-- constraint closes: two concurrent recomputations of the same param could both see "not found"
-- and insert two rows for the same key. Also removes a sequential scan on every read.
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS UX_SYS_PARAM_NAME ON product.SYS_PARAM (NAME);
ALTER TABLE product.SYS_PARAM ADD CONSTRAINT UK_SYS_PARAM_NAME UNIQUE USING INDEX UX_SYS_PARAM_NAME;

-- =============================================================================
-- PIPELINE_METRICS — TRACE_ID
--
-- "One row per RAG pipeline execution" (table comment, DKP-0010) — TRACE_ID is the natural key for
-- correlating a metrics row back to application/tracing logs when debugging a specific request,
-- but had no index at all. Plain, not unique: this is an append-only analytics table with no
-- write-time race to protect against, so the stronger guarantee isn't worth even the small risk of
-- rejecting an insert on a hypothetical trace-id collision from an upstream retry.
-- =============================================================================

CREATE INDEX IF NOT EXISTS IDX_PIPELINE_METRICS_TRACE_ID ON product.PIPELINE_METRICS (TRACE_ID);

-- =============================================================================
-- CONTENT_EMBEDDING — (CONTENT_ITEM_ID, MODEL_NAME, CHUNK_INDEX)
--
-- MODEL_NAME is part of the natural key, not just CONTENT_ITEM_ID + CHUNK_INDEX: a content item
-- can have chunk sets from more than one embedding model at once (ContentIngestionServiceImpl
-- deletes by contentItem + model before re-ingesting, implying both coexist across models).
-- Duplicates would only arise from a chunking bug within a single ingestion run, since re-
-- ingestion already deletes the prior set first — this is cheap insurance against that bug, not a
-- live concurrency race.
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS UX_CONTENT_EMBEDDING_ITEM_MODEL_CHUNK
    ON product.CONTENT_EMBEDDING (CONTENT_ITEM_ID, MODEL_NAME, CHUNK_INDEX);
ALTER TABLE product.CONTENT_EMBEDDING
    ADD CONSTRAINT UK_CONTENT_EMBEDDING_ITEM_MODEL_CHUNK UNIQUE USING INDEX UX_CONTENT_EMBEDDING_ITEM_MODEL_CHUNK;
