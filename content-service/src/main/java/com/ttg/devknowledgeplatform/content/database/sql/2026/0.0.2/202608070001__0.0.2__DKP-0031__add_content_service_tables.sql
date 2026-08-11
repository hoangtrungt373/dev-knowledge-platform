-- liquibase formatted sql
-- changeset ttg:202608070001__0.0.2__DKP-0031__add_content_service_tables logicalFilePath:ContentService
-- comment: Add content-service's own CATEGORY/TAG/CONTENT_ITEM/CONTENT_ITEM_TAG/QUESTION_ANSWER/ARTICLE tables in a new `content` schema
--
-- Table shape is a fresh snapshot of gateway's product.CATEGORY/TAG/CONTENT_ITEM/CONTENT_ITEM_TAG/
-- QUESTION_ANSWER/ARTICLE as of DKP-0018 (init_tables through add_missing_unique_constraints_and_indexes),
-- not a replay of that migration's own incremental history (DKP-0001/0002 init, DKP-0003 TAG.STATUS,
-- DKP-0004 ARTICLE, DKP-0009 QUALITY_SCORE, DKP-0013 SEED_ID, DKP-0014 INTERVIEW_QUESTION ->
-- QUESTION_ANSWER rename, DKP-0018 unique constraints/indexes) — same convention task-service's
-- DKP-0028 and social-service's DKP-0029/0030 already followed for their own tables.
--
-- CONTENT_ITEM.AUTHOR_UUID (VARCHAR(36), the Keycloak JWT's `sub` claim) replaces gateway's
-- plain AUTHOR_ID INTEGER column outright — this changeset had never run against any real
-- database when this module's standalone app shell landed (same session), so it was edited
-- directly rather than adding a follow-up ALTER changeset, mirroring task-service's own
-- ownerId->ownerUuid correction (see the project-microservices-extraction-plan memory). Claims-based,
-- no foreign key onto a local USER table: this module resolves "who is the caller" straight from
-- the verified JWT via KeycloakJwtAuthenticationConverter (no persistence, mirrors
-- task-service's/ecommerce-service's converter) rather than JIT-provisioning its own User copy —
-- nothing ever reads this column back or joins through it (see ArticleController/
-- QuestionAnswerController — write-once at creation), so there is no "display another user's
-- profile" need to justify a persisted local User row.

CREATE SCHEMA IF NOT EXISTS content;

-- =============================================================================
-- CATEGORY
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS content.CATEGORY_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS content.CATEGORY (
    CATEGORY_ID             INTEGER                         NOT NULL,
    PARENT_ID               INTEGER,
    NAME                    VARCHAR(100)                    NOT NULL,
    SLUG                    VARCHAR(100)                    NOT NULL,
    SEED_ID                 VARCHAR(100),
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_CATEGORY PRIMARY KEY (CATEGORY_ID),
    CONSTRAINT FK_CATEGORY_PARENT FOREIGN KEY (PARENT_ID) REFERENCES content.CATEGORY (CATEGORY_ID)
);

ALTER SEQUENCE content.CATEGORY_SEQ OWNED BY content.CATEGORY.CATEGORY_ID;

CREATE INDEX IF NOT EXISTS IDX_CATEGORY_PARENT ON content.CATEGORY (PARENT_ID);

CREATE UNIQUE INDEX IF NOT EXISTS UX_CATEGORY_SLUG ON content.CATEGORY (SLUG);
ALTER TABLE content.CATEGORY ADD CONSTRAINT UK_CATEGORY_SLUG UNIQUE USING INDEX UX_CATEGORY_SLUG;

-- Functional index, not a named UNIQUE constraint: Postgres's "ADD CONSTRAINT ... UNIQUE USING
-- INDEX" requires a plain-column index, not an expression index. Backs CategoryServiceImpl's
-- existsByNameIgnoreCase/existsByNameIgnoreCaseAndIdNot dedup check.
CREATE UNIQUE INDEX IF NOT EXISTS UX_CATEGORY_NAME_LOWER ON content.CATEGORY (LOWER(NAME));

-- Nullable, seed-only column — see CategorySeeder's idempotency check. A UNIQUE index (not NOT
-- NULL) lets every non-seeded row share NULL; Postgres treats NULLs as distinct under a unique index.
CREATE UNIQUE INDEX IF NOT EXISTS UX_CATEGORY_SEED_ID ON content.CATEGORY (SEED_ID);

-- =============================================================================
-- TAG
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS content.TAG_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS content.TAG (
    TAG_ID                  INTEGER                         NOT NULL,
    NAME                    VARCHAR(100)                    NOT NULL,
    SLUG                    VARCHAR(100)                    NOT NULL,
    STATUS                  VARCHAR(10)                     NOT NULL,
    SEED_ID                 VARCHAR(100),
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_TAG PRIMARY KEY (TAG_ID),
    CONSTRAINT CKC_TAG_STATUS CHECK (STATUS IN ('ACTIVE', 'INACTIVE'))
);

ALTER SEQUENCE content.TAG_SEQ OWNED BY content.TAG.TAG_ID;

CREATE UNIQUE INDEX IF NOT EXISTS UX_TAG_SLUG ON content.TAG (SLUG);
ALTER TABLE content.TAG ADD CONSTRAINT UK_TAG_SLUG UNIQUE USING INDEX UX_TAG_SLUG;

CREATE UNIQUE INDEX IF NOT EXISTS UX_TAG_NAME_LOWER ON content.TAG (LOWER(NAME));

CREATE UNIQUE INDEX IF NOT EXISTS UX_TAG_SEED_ID ON content.TAG (SEED_ID);

-- =============================================================================
-- CONTENT_ITEM
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS content.CONTENT_ITEM_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS content.CONTENT_ITEM (
    CONTENT_ITEM_ID         INTEGER                         NOT NULL,
    TYPE                    VARCHAR(50)                     NOT NULL,
    STATUS                  VARCHAR(50)                     NOT NULL,
    TITLE                   VARCHAR(500)                    NOT NULL,
    SLUG                    VARCHAR(500)                    NOT NULL,
    AUTHOR_UUID              VARCHAR(36),
    CATEGORY_ID             INTEGER,
    VIEW_COUNT              INTEGER                         NOT NULL    DEFAULT 0,
    PUBLISHED_AT            TIMESTAMP WITH TIME ZONE,
    QUALITY_SCORE           DECIMAL(5, 4),
    SEED_ID                 VARCHAR(100),
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_CONTENT_ITEM PRIMARY KEY (CONTENT_ITEM_ID),
    CONSTRAINT FK_CONTENT_ITEM_CATEGORY FOREIGN KEY (CATEGORY_ID) REFERENCES content.CATEGORY (CATEGORY_ID),

    CONSTRAINT CKC_CONTENT_ITEM_TYPE CHECK (TYPE IN ('QUESTION_ANSWER', 'ARTICLE', 'BLOG_POST')),
    CONSTRAINT CKC_CONTENT_ITEM_STATUS CHECK (STATUS IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

ALTER SEQUENCE content.CONTENT_ITEM_SEQ OWNED BY content.CONTENT_ITEM.CONTENT_ITEM_ID;

CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_TYPE ON content.CONTENT_ITEM (TYPE);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_STATUS ON content.CONTENT_ITEM (STATUS);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_CATEGORY ON content.CONTENT_ITEM (CATEGORY_ID);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_AUTHOR ON content.CONTENT_ITEM (AUTHOR_UUID);

CREATE UNIQUE INDEX IF NOT EXISTS UX_CONTENT_ITEM_SLUG ON content.CONTENT_ITEM (SLUG);
ALTER TABLE content.CONTENT_ITEM ADD CONSTRAINT UK_CONTENT_ITEM_SLUG UNIQUE USING INDEX UX_CONTENT_ITEM_SLUG;

CREATE UNIQUE INDEX IF NOT EXISTS UX_CONTENT_ITEM_SEED_ID ON content.CONTENT_ITEM (SEED_ID);

-- =============================================================================
-- CONTENT_ITEM_TAG
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS content.CONTENT_ITEM_TAG_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS content.CONTENT_ITEM_TAG (
    CONTENT_ITEM_TAG_ID     INTEGER                         NOT NULL,
    CONTENT_ITEM_ID         INTEGER                         NOT NULL,
    TAG_ID                  INTEGER                         NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_CONTENT_ITEM_TAG PRIMARY KEY (CONTENT_ITEM_TAG_ID),
    CONSTRAINT FK_CONTENT_ITEM_TAG_CONTENT FOREIGN KEY (CONTENT_ITEM_ID) REFERENCES content.CONTENT_ITEM (CONTENT_ITEM_ID),
    CONSTRAINT FK_CONTENT_ITEM_TAG_TAG FOREIGN KEY (TAG_ID) REFERENCES content.TAG (TAG_ID)
);

ALTER SEQUENCE content.CONTENT_ITEM_TAG_SEQ OWNED BY content.CONTENT_ITEM_TAG.CONTENT_ITEM_TAG_ID;

CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_TAG_CONTENT_ITEM ON content.CONTENT_ITEM_TAG (CONTENT_ITEM_ID);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_TAG_TAG ON content.CONTENT_ITEM_TAG (TAG_ID);

CREATE UNIQUE INDEX IF NOT EXISTS UX_CONTENT_ITEM_TAG_PAIR ON content.CONTENT_ITEM_TAG (CONTENT_ITEM_ID, TAG_ID);
ALTER TABLE content.CONTENT_ITEM_TAG
    ADD CONSTRAINT UK_CONTENT_ITEM_TAG_PAIR UNIQUE USING INDEX UX_CONTENT_ITEM_TAG_PAIR;

-- =============================================================================
-- QUESTION_ANSWER
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS content.QUESTION_ANSWER_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS content.QUESTION_ANSWER (
    QUESTION_ANSWER_ID      INTEGER                         NOT NULL,
    CONTENT_ITEM_ID         INTEGER                         NOT NULL,
    DIFFICULTY              VARCHAR(50),
    QUESTION_BODY           TEXT                            NOT NULL,
    SHORT_ANSWER            TEXT,
    DETAILED_ANSWER         TEXT,
    IS_COMMON               BOOLEAN,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_QUESTION_ANSWER PRIMARY KEY (QUESTION_ANSWER_ID),
    CONSTRAINT FK_QUESTION_ANSWER_CONTENT FOREIGN KEY (CONTENT_ITEM_ID) REFERENCES content.CONTENT_ITEM (CONTENT_ITEM_ID),

    CONSTRAINT CKC_QUESTION_ANSWER_DIFFICULTY CHECK (DIFFICULTY IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'))
);

ALTER SEQUENCE content.QUESTION_ANSWER_SEQ OWNED BY content.QUESTION_ANSWER.QUESTION_ANSWER_ID;

CREATE INDEX IF NOT EXISTS IDX_QUESTION_ANSWER_DIFFICULTY ON content.QUESTION_ANSWER (DIFFICULTY);

CREATE UNIQUE INDEX IF NOT EXISTS UX_QUESTION_ANSWER_CONTENT ON content.QUESTION_ANSWER (CONTENT_ITEM_ID);
ALTER TABLE content.QUESTION_ANSWER
    ADD CONSTRAINT UK_QUESTION_ANSWER_CONTENT UNIQUE USING INDEX UX_QUESTION_ANSWER_CONTENT;

-- =============================================================================
-- ARTICLE
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS content.ARTICLE_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS content.ARTICLE (
    ARTICLE_ID              INTEGER                         NOT NULL,
    CONTENT_ITEM_ID         INTEGER                         NOT NULL,
    BODY                    TEXT,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_ARTICLE PRIMARY KEY (ARTICLE_ID),
    CONSTRAINT FK_ARTICLE_CONTENT_ITEM FOREIGN KEY (CONTENT_ITEM_ID) REFERENCES content.CONTENT_ITEM (CONTENT_ITEM_ID)
);

ALTER SEQUENCE content.ARTICLE_SEQ OWNED BY content.ARTICLE.ARTICLE_ID;

CREATE UNIQUE INDEX IF NOT EXISTS UX_ARTICLE_CONTENT_ITEM ON content.ARTICLE (CONTENT_ITEM_ID);
ALTER TABLE content.ARTICLE
    ADD CONSTRAINT UK_ARTICLE_CONTENT_ITEM UNIQUE USING INDEX UX_ARTICLE_CONTENT_ITEM;
