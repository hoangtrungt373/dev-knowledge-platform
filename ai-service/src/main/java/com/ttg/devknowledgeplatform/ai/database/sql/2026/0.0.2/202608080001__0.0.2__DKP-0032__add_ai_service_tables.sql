-- liquibase formatted sql
-- changeset ttg:202608080001__0.0.2__DKP-0032__add_ai_service_tables logicalFilePath:AiService
-- comment: Add ai-service's own CONTENT_EMBEDDING/CHAT_SESSION/CHAT_MESSAGE/SYS_PARAM/PIPELINE_METRICS tables in a new `ai` schema
--
-- Table shape is a fresh snapshot of gateway's product.CONTENT_EMBEDDING/CHAT_SESSION/CHAT_MESSAGE/
-- SYS_PARAM/PIPELINE_METRICS as of DKP-0018 (init_ai_tables through add_missing_unique_constraints_and_indexes),
-- not a replay of that migration's own incremental history (DKP-0005 init, DKP-0006 chat tables,
-- DKP-0007 chat summary, DKP-0008 sys_param, DKP-0010/0011/0012 pipeline_metrics, DKP-0014
-- INTERVIEW_QUESTION -> QUESTION_ANSWER rename, DKP-0018 unique constraints/indexes) — same
-- convention task-service's DKP-0028, social-service's DKP-0029/0030, and content-service's
-- DKP-0031 already followed for their own tables.
--
-- Two real ownership-model changes from gateway's tree, not just a schema rename:
--
--   1. CHAT_SESSION.USER_UUID (VARCHAR(36)) replaces gateway's USER_ID INTEGER FK to product.USER
--      entirely, and PIPELINE_METRICS.USER_UUID replaces its own USER_ID INTEGER (which was never
--      a foreign key to begin with — an analytics table must survive user deletion). Claims-based,
--      no local USER table: this module resolves "who is the caller" straight from the verified
--      JWT via KeycloakJwtAuthenticationConverter (no persistence, mirrors task-service's/
--      ecommerce-service's/content-service's converter) rather than JIT-provisioning its own User
--      copy — nothing in this module ever reads either column back to display another user's
--      profile, only to compare/attribute against the caller's own UUID (see ai-service/CLAUDE.md's
--      "No local User copy" rule).
--
--   2. CONTENT_EMBEDDING has no foreign key to CONTENT_ITEM here — that table now lives in
--      content-service's own `content` schema (a different service's database entirely once both
--      are actually standalone), so a cross-schema FK isn't possible. CONTENT_ITEM_ID stays a
--      plain column, populated via ai-service's own ContentServiceClient (HTTP) rather than a JPA
--      association — see ai-service/CLAUDE.md and content-service/CLAUDE.md.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE SCHEMA IF NOT EXISTS ai;

-- =============================================================================
-- CONTENT_EMBEDDING
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ai.CONTENT_EMBEDDING_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS ai.CONTENT_EMBEDDING (
    CONTENT_EMBEDDING_ID    INTEGER                         NOT NULL,
    CONTENT_ITEM_ID         INTEGER                         NOT NULL,
    SOURCE_TYPE             VARCHAR(50)                     NOT NULL,
    CHUNK_INDEX             INTEGER                         NOT NULL,
    CHUNK_TEXT              TEXT                            NOT NULL,
    EMBEDDING               VECTOR(1536)                    NOT NULL,
    MODEL_NAME              VARCHAR(100)                    NOT NULL,
    DIMENSIONS              INTEGER                         NOT NULL,
    TOKEN_COUNT             INTEGER,
    METADATA                JSONB,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_CONTENT_EMBEDDING PRIMARY KEY (CONTENT_EMBEDDING_ID),
    CONSTRAINT CKC_CONTENT_EMBEDDING_SOURCE_TYPE CHECK (SOURCE_TYPE IN ('QUESTION_ANSWER', 'ARTICLE', 'BLOG_POST'))
);

ALTER SEQUENCE ai.CONTENT_EMBEDDING_SEQ OWNED BY ai.CONTENT_EMBEDDING.CONTENT_EMBEDDING_ID;

CREATE INDEX IF NOT EXISTS IDX_CONTENT_EMBEDDING_CONTENT_ITEM ON ai.CONTENT_EMBEDDING (CONTENT_ITEM_ID);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_EMBEDDING_SOURCE_TYPE  ON ai.CONTENT_EMBEDDING (SOURCE_TYPE);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_EMBEDDING_MODEL        ON ai.CONTENT_EMBEDDING (MODEL_NAME);

-- HNSW index for approximate nearest-neighbor search using cosine distance (<=>).
CREATE INDEX IF NOT EXISTS IDX_CONTENT_EMBEDDING_HNSW
    ON ai.CONTENT_EMBEDDING USING hnsw (EMBEDDING vector_cosine_ops);

CREATE UNIQUE INDEX IF NOT EXISTS UX_CONTENT_EMBEDDING_ITEM_MODEL_CHUNK
    ON ai.CONTENT_EMBEDDING (CONTENT_ITEM_ID, MODEL_NAME, CHUNK_INDEX);
ALTER TABLE ai.CONTENT_EMBEDDING
    ADD CONSTRAINT UK_CONTENT_EMBEDDING_ITEM_MODEL_CHUNK UNIQUE USING INDEX UX_CONTENT_EMBEDDING_ITEM_MODEL_CHUNK;

-- =============================================================================
-- CHAT_SESSION
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ai.CHAT_SESSION_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS ai.CHAT_SESSION (
    CHAT_SESSION_ID         INTEGER                         NOT NULL,
    USER_UUID               VARCHAR(36)                     NOT NULL,
    TITLE                   VARCHAR(500),
    LAST_ACTIVITY_AT        TIMESTAMP WITH TIME ZONE        NOT NULL,
    SUMMARY                 TEXT,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_CHAT_SESSION PRIMARY KEY (CHAT_SESSION_ID)
);

ALTER SEQUENCE ai.CHAT_SESSION_SEQ OWNED BY ai.CHAT_SESSION.CHAT_SESSION_ID;

CREATE INDEX IF NOT EXISTS IDX_CHAT_SESSION_USER_UUID     ON ai.CHAT_SESSION (USER_UUID);
CREATE INDEX IF NOT EXISTS IDX_CHAT_SESSION_LAST_ACTIVITY ON ai.CHAT_SESSION (LAST_ACTIVITY_AT);

-- =============================================================================
-- CHAT_MESSAGE
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ai.CHAT_MESSAGE_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS ai.CHAT_MESSAGE (
    CHAT_MESSAGE_ID         INTEGER                         NOT NULL,
    CHAT_SESSION_ID         INTEGER                         NOT NULL,
    ROLE                    VARCHAR(20)                     NOT NULL,
    CONTENT                 TEXT                            NOT NULL,
    TURN_INDEX              INTEGER                         NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_CHAT_MESSAGE PRIMARY KEY (CHAT_MESSAGE_ID),
    CONSTRAINT FK_CHAT_MESSAGE_SESSION FOREIGN KEY (CHAT_SESSION_ID) REFERENCES ai.CHAT_SESSION (CHAT_SESSION_ID),
    CONSTRAINT CKC_CHAT_MESSAGE_ROLE CHECK (ROLE IN ('USER', 'ASSISTANT'))
);

ALTER SEQUENCE ai.CHAT_MESSAGE_SEQ OWNED BY ai.CHAT_MESSAGE.CHAT_MESSAGE_ID;

CREATE INDEX IF NOT EXISTS IDX_CHAT_MESSAGE_SESSION_ID ON ai.CHAT_MESSAGE (CHAT_SESSION_ID);

CREATE UNIQUE INDEX IF NOT EXISTS UX_CHAT_MESSAGE_TURN_ORDER ON ai.CHAT_MESSAGE (CHAT_SESSION_ID, TURN_INDEX);
ALTER TABLE ai.CHAT_MESSAGE
    ADD CONSTRAINT UK_CHAT_MESSAGE_TURN_ORDER UNIQUE USING INDEX UX_CHAT_MESSAGE_TURN_ORDER;

-- =============================================================================
-- SYS_PARAM
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ai.SYS_PARAM_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS ai.SYS_PARAM (
    SYS_PARAM_ID            INTEGER                         NOT NULL,
    NAME                    VARCHAR(100)                    NOT NULL,
    VALUE                   TEXT                            NOT NULL,
    COMPUTED_AT             TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_SYS_PARAM PRIMARY KEY (SYS_PARAM_ID)
);

ALTER SEQUENCE ai.SYS_PARAM_SEQ OWNED BY ai.SYS_PARAM.SYS_PARAM_ID;

CREATE UNIQUE INDEX IF NOT EXISTS UX_SYS_PARAM_NAME ON ai.SYS_PARAM (NAME);
ALTER TABLE ai.SYS_PARAM ADD CONSTRAINT UK_SYS_PARAM_NAME UNIQUE USING INDEX UX_SYS_PARAM_NAME;

-- =============================================================================
-- PIPELINE_METRICS
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ai.PIPELINE_METRICS_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS ai.PIPELINE_METRICS (
    PIPELINE_METRICS_ID              INTEGER                         NOT NULL,
    TRACE_ID                         VARCHAR(36)                     NOT NULL,
    CREATED_AT                       TIMESTAMP WITH TIME ZONE        NOT NULL,
    ABORTED_AT                       VARCHAR(100),
    CANDIDATE_COUNT                  INTEGER,
    AFTER_SCORING_COUNT              INTEGER,
    SELECTED_COUNT                   INTEGER,
    EVIDENCE_MEAN_SCORE              DECIMAL(5, 4),
    EFFECTIVE_SIM_THRESHOLD          DECIMAL(5, 4),
    ANSWER_CONTEXT_SIM               DECIMAL(5, 4),
    ANSWER_QUERY_SIM                 DECIMAL(5, 4),
    ANSWER_DRIFTED                   BOOLEAN,
    CONTEXTUALIZATION_MS             BIGINT,
    EMBEDDING_MS                     BIGINT,
    RETRIEVAL_MS                     BIGINT,
    LLM_GENERATION_MS                BIGINT,
    TOTAL_PIPELINE_MS                BIGINT,
    CONTEXTUALIZATION_INPUT_TOKENS   INTEGER,
    CONTEXTUALIZATION_OUTPUT_TOKENS  INTEGER,
    EMBEDDING_TOKENS                 INTEGER,
    QUALITY_EMBEDDING_TOKENS         INTEGER,
    GENERATION_INPUT_TOKENS          INTEGER,
    GENERATION_OUTPUT_TOKENS         INTEGER,
    ESTIMATED_COST_USD               DECIMAL(12, 8),
    USER_UUID                        VARCHAR(36),
    CHAT_MODEL                       VARCHAR(100),

    CONSTRAINT PK_PIPELINE_METRICS PRIMARY KEY (PIPELINE_METRICS_ID)
);

ALTER SEQUENCE ai.PIPELINE_METRICS_SEQ OWNED BY ai.PIPELINE_METRICS.PIPELINE_METRICS_ID;

CREATE INDEX IF NOT EXISTS IDX_PIPELINE_METRICS_CREATED_AT
    ON ai.PIPELINE_METRICS (CREATED_AT DESC);

CREATE INDEX IF NOT EXISTS IDX_PIPELINE_METRICS_ABORTED_AT
    ON ai.PIPELINE_METRICS (ABORTED_AT)
    WHERE ABORTED_AT IS NOT NULL;

CREATE INDEX IF NOT EXISTS IDX_PIPELINE_METRICS_USER_UUID
    ON ai.PIPELINE_METRICS (USER_UUID)
    WHERE USER_UUID IS NOT NULL;

CREATE INDEX IF NOT EXISTS IDX_PIPELINE_METRICS_CHAT_MODEL
    ON ai.PIPELINE_METRICS (CHAT_MODEL)
    WHERE CHAT_MODEL IS NOT NULL;

CREATE INDEX IF NOT EXISTS IDX_PIPELINE_METRICS_TRACE_ID ON ai.PIPELINE_METRICS (TRACE_ID);
