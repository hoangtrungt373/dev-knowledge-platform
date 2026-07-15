-- liquibase formatted sql
-- changeset ttg:202606170001__0.0.1__DKP-0005__init_ai_tables logicalFilePath:DevKnowledgePlatform
-- comment: Add CONTENT_EMBEDDING table for RAG vector search (pgvector)

-- =============================================================================
-- pgvector extension — required for VECTOR(1536) column type and HNSW index.
-- Must run before any table that uses the VECTOR type.
-- Requires the database user to have SUPERUSER or CREATE EXTENSION privilege.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- =============================================================================
-- CONTENT_EMBEDDING
-- Each row is one text chunk from a published content item, stored alongside
-- its 1536-dimensional OpenAI embedding for cosine-similarity RAG retrieval.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.CONTENT_EMBEDDING_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.CONTENT_EMBEDDING (
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

    CONSTRAINT FK_CONTENT_EMBEDDING_CONTENT_ITEM FOREIGN KEY (CONTENT_ITEM_ID)
        REFERENCES product.CONTENT_ITEM (CONTENT_ITEM_ID),

    CONSTRAINT CKC_CONTENT_EMBEDDING_SOURCE_TYPE
        CHECK (SOURCE_TYPE IN ('INTERVIEW_QUESTION', 'ARTICLE', 'BLOG_POST'))
);

ALTER SEQUENCE product.CONTENT_EMBEDDING_SEQ OWNED BY product.CONTENT_EMBEDDING.CONTENT_EMBEDDING_ID;

-- Standard lookup indexes
CREATE INDEX IF NOT EXISTS IDX_CONTENT_EMBEDDING_CONTENT_ITEM ON product.CONTENT_EMBEDDING (CONTENT_ITEM_ID);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_EMBEDDING_SOURCE_TYPE  ON product.CONTENT_EMBEDDING (SOURCE_TYPE);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_EMBEDDING_MODEL        ON product.CONTENT_EMBEDDING (MODEL_NAME);

-- HNSW index for approximate nearest-neighbor search using cosine distance (<=>).
-- m=16, ef_construction=64 are pgvector defaults: good recall/build-time balance.
-- Use vector_l2_ops instead if switching to Euclidean distance.
CREATE INDEX IF NOT EXISTS IDX_CONTENT_EMBEDDING_HNSW
    ON product.CONTENT_EMBEDDING USING hnsw (EMBEDDING vector_cosine_ops);
