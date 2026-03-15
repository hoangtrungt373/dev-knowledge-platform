-- =============================================================================
-- Extensions
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- =============================================================================
-- Schema
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS product;

-- =============================================================================
-- USER
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.USER_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.USER (
    USER_ID                 INTEGER                         NOT NULL,
    EMAIL                   VARCHAR(255)                    NOT NULL,
    USERNAME                VARCHAR(255)                    NOT NULL,
    PASSWORD                VARCHAR(255)                    NOT NULL,
    FIRST_NAME              VARCHAR(255),
    LAST_NAME               VARCHAR(255),
    PROFILE_PICTURE         VARCHAR(500),
    PROVIDER                VARCHAR(50)                     NOT NULL,
    PROVIDER_ID             VARCHAR(255),
    ROLE                    VARCHAR(50)                     NOT NULL,
    EMAIL_VERIFIED          BOOLEAN                         NOT NULL,
    STATUS                  VARCHAR(50)                     NOT NULL,
    ENABLED                 BOOLEAN                         NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_USER PRIMARY KEY (USER_ID),

    CONSTRAINT UQ_USER_EMAIL UNIQUE (EMAIL),
    CONSTRAINT UQ_USER_USERNAME UNIQUE (USERNAME),

    CONSTRAINT CKC_USER_PROVIDER CHECK (PROVIDER IN ('LOCAL', 'GOOGLE', 'FACEBOOK')),
    CONSTRAINT CKC_USER_STATUS CHECK (STATUS IN ('ONLINE', 'OFFLINE', 'AWAY', 'BUSY')),
    CONSTRAINT CKC_USER_ROLE CHECK (ROLE IN ('USER', 'ADMIN'))
);

ALTER SEQUENCE product.USER_SEQ OWNED BY product.USER.USER_ID;

CREATE INDEX IF NOT EXISTS IDX_USER_EMAIL ON product.USER (EMAIL);
CREATE INDEX IF NOT EXISTS IDX_USER_USERNAME ON product.USER (USERNAME);

-- =============================================================================
-- CATEGORY
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.CATEGORY_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.CATEGORY (
    CATEGORY_ID             INTEGER                         NOT NULL,
    PARENT_ID               INTEGER,
    NAME                    VARCHAR(100)                    NOT NULL,
    SLUG                    VARCHAR(100)                    NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_CATEGORY PRIMARY KEY (CATEGORY_ID),

    CONSTRAINT FK_CATEGORY_PARENT FOREIGN KEY (PARENT_ID) REFERENCES product.CATEGORY (CATEGORY_ID),

    CONSTRAINT UQ_CATEGORY_SLUG UNIQUE (SLUG)
);

ALTER SEQUENCE product.CATEGORY_SEQ OWNED BY product.CATEGORY.CATEGORY_ID;

CREATE INDEX IF NOT EXISTS IDX_CATEGORY_SLUG ON product.CATEGORY (SLUG);
CREATE INDEX IF NOT EXISTS IDX_CATEGORY_PARENT ON product.CATEGORY (PARENT_ID);

-- =============================================================================
-- TAG
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.TAG_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.TAG (
    TAG_ID                  INTEGER                         NOT NULL,
    NAME                    VARCHAR(100)                    NOT NULL,
    SLUG                    VARCHAR(100)                    NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_TAG PRIMARY KEY (TAG_ID),

    CONSTRAINT UQ_TAG_NAME UNIQUE (NAME),
    CONSTRAINT UQ_TAG_SLUG UNIQUE (SLUG)
);

ALTER SEQUENCE product.TAG_SEQ OWNED BY product.TAG.TAG_ID;

CREATE INDEX IF NOT EXISTS IDX_TAG_SLUG ON product.TAG (SLUG);

-- =============================================================================
-- CONTENT_ITEM
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.CONTENT_ITEM_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.CONTENT_ITEM (
    CONTENT_ITEM_ID         INTEGER                         NOT NULL,
    TYPE                    VARCHAR(50)                     NOT NULL,
    STATUS                  VARCHAR(50)                     NOT NULL,
    TITLE                   VARCHAR(500)                    NOT NULL,
    SLUG                    VARCHAR(500)                    NOT NULL,
    AUTHOR_ID               INTEGER,
    CATEGORY_ID             INTEGER,
    VIEW_COUNT              INTEGER                         NOT NULL    DEFAULT 0,
    PUBLISHED_AT            TIMESTAMP WITH TIME ZONE,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_CONTENT_ITEM PRIMARY KEY (CONTENT_ITEM_ID),

    CONSTRAINT FK_CONTENT_ITEM_CATEGORY FOREIGN KEY (CATEGORY_ID) REFERENCES product.CATEGORY (CATEGORY_ID),

    CONSTRAINT UQ_CONTENT_ITEM_SLUG UNIQUE (SLUG),

    CONSTRAINT CKC_CONTENT_ITEM_TYPE CHECK (TYPE IN ('INTERVIEW_QUESTION', 'ARTICLE', 'BLOG_POST')),
    CONSTRAINT CKC_CONTENT_ITEM_STATUS CHECK (STATUS IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

ALTER SEQUENCE product.CONTENT_ITEM_SEQ OWNED BY product.CONTENT_ITEM.CONTENT_ITEM_ID;

CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_SLUG ON product.CONTENT_ITEM (SLUG);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_TYPE ON product.CONTENT_ITEM (TYPE);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_STATUS ON product.CONTENT_ITEM (STATUS);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_CATEGORY ON product.CONTENT_ITEM (CATEGORY_ID);

-- =============================================================================
-- CONTENT_ITEM_TAG (many-to-many)
-- =============================================================================

CREATE TABLE IF NOT EXISTS product.CONTENT_ITEM_TAG (
    CONTENT_ITEM_ID         INTEGER                         NOT NULL,
    TAG_ID                  INTEGER                         NOT NULL,

    CONSTRAINT PK_CONTENT_ITEM_TAG PRIMARY KEY (CONTENT_ITEM_ID, TAG_ID),

    CONSTRAINT FK_CONTENT_ITEM_TAG_CONTENT FOREIGN KEY (CONTENT_ITEM_ID) REFERENCES product.CONTENT_ITEM (CONTENT_ITEM_ID) ON DELETE CASCADE,
    CONSTRAINT FK_CONTENT_ITEM_TAG_TAG FOREIGN KEY (TAG_ID) REFERENCES product.TAG (TAG_ID) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_TAG_CONTENT ON product.CONTENT_ITEM_TAG (CONTENT_ITEM_ID);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_TAG_TAG ON product.CONTENT_ITEM_TAG (TAG_ID);

-- =============================================================================
-- INTERVIEW_QUESTION
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.INTERVIEW_QUESTION_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.INTERVIEW_QUESTION (
    INTERVIEW_QUESTION_ID   INTEGER                         NOT NULL,
    CONTENT_ITEM_ID         INTEGER                         NOT NULL,
    DIFFICULTY              VARCHAR(50)                     NOT NULL    DEFAULT 'INTERMEDIATE',
    QUESTION_BODY           TEXT                            NOT NULL,
    SHORT_ANSWER            TEXT,
    DETAILED_ANSWER         TEXT,
    IS_COMMON               BOOLEAN                         NOT NULL    DEFAULT FALSE,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_INTERVIEW_QUESTION PRIMARY KEY (INTERVIEW_QUESTION_ID),

    CONSTRAINT FK_INTERVIEW_QUESTION_CONTENT FOREIGN KEY (CONTENT_ITEM_ID) REFERENCES product.CONTENT_ITEM (CONTENT_ITEM_ID) ON DELETE CASCADE,

    CONSTRAINT UQ_INTERVIEW_QUESTION_CONTENT UNIQUE (CONTENT_ITEM_ID),

    CONSTRAINT CKC_INTERVIEW_QUESTION_DIFFICULTY CHECK (DIFFICULTY IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'))
);

ALTER SEQUENCE product.INTERVIEW_QUESTION_SEQ OWNED BY product.INTERVIEW_QUESTION.INTERVIEW_QUESTION_ID;

CREATE INDEX IF NOT EXISTS IDX_INTERVIEW_QUESTION_CONTENT ON product.INTERVIEW_QUESTION (CONTENT_ITEM_ID);
CREATE INDEX IF NOT EXISTS IDX_INTERVIEW_QUESTION_DIFFICULTY ON product.INTERVIEW_QUESTION (DIFFICULTY);

-- =============================================================================
-- CONTENT_EMBEDDING (vector store for RAG)
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
    CHUNK_INDEX             SMALLINT                        NOT NULL,
    CHUNK_TEXT              TEXT                            NOT NULL,
    EMBEDDING               VECTOR                          NOT NULL,
    MODEL_NAME              VARCHAR(100)                    NOT NULL,
    DIMENSIONS              SMALLINT                        NOT NULL,
    TOKEN_COUNT             SMALLINT,
    METADATA                JSONB,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_CONTENT_EMBEDDING PRIMARY KEY (CONTENT_EMBEDDING_ID),

    CONSTRAINT FK_CONTENT_EMBEDDING_CONTENT FOREIGN KEY (CONTENT_ITEM_ID) REFERENCES product.CONTENT_ITEM (CONTENT_ITEM_ID) ON DELETE CASCADE,

    CONSTRAINT UQ_CONTENT_EMBEDDING_CHUNK UNIQUE (CONTENT_ITEM_ID, CHUNK_INDEX, MODEL_NAME),

    CONSTRAINT CKC_CONTENT_EMBEDDING_SOURCE CHECK (SOURCE_TYPE IN ('INTERVIEW_QUESTION', 'ARTICLE', 'BLOG_POST'))
);

ALTER SEQUENCE product.CONTENT_EMBEDDING_SEQ OWNED BY product.CONTENT_EMBEDDING.CONTENT_EMBEDDING_ID;

CREATE INDEX IF NOT EXISTS IDX_CONTENT_EMBEDDING_CONTENT ON product.CONTENT_EMBEDDING (CONTENT_ITEM_ID);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_EMBEDDING_SOURCE ON product.CONTENT_EMBEDDING (SOURCE_TYPE);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_EMBEDDING_METADATA ON product.CONTENT_EMBEDDING USING GIN (METADATA);

CREATE INDEX IF NOT EXISTS IDX_CONTENT_EMBEDDING_HNSW_SMALL
    ON product.CONTENT_EMBEDDING USING HNSW (EMBEDDING vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE MODEL_NAME = 'text-embedding-3-small';

CREATE INDEX IF NOT EXISTS IDX_CONTENT_EMBEDDING_HNSW_MINI
    ON product.CONTENT_EMBEDDING USING HNSW (EMBEDDING vector_cosine_ops)
    WITH (m = 16, ef_construction = 64)
    WHERE MODEL_NAME = 'all-MiniLM-L6-v2';

-- =============================================================================
-- SEARCH_DOCUMENT (GIN full-text search)
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.SEARCH_DOCUMENT_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.SEARCH_DOCUMENT (
    SEARCH_DOCUMENT_ID      INTEGER                         NOT NULL,
    CONTENT_ITEM_ID         INTEGER                         NOT NULL,
    TITLE                   TEXT                            NOT NULL,
    BODY_PLAIN              TEXT                            NOT NULL,
    SEARCH_VECTOR           TSVECTOR                        NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_SEARCH_DOCUMENT PRIMARY KEY (SEARCH_DOCUMENT_ID),

    CONSTRAINT FK_SEARCH_DOCUMENT_CONTENT FOREIGN KEY (CONTENT_ITEM_ID) REFERENCES product.CONTENT_ITEM (CONTENT_ITEM_ID) ON DELETE CASCADE,

    CONSTRAINT UQ_SEARCH_DOCUMENT_CONTENT UNIQUE (CONTENT_ITEM_ID)
);

ALTER SEQUENCE product.SEARCH_DOCUMENT_SEQ OWNED BY product.SEARCH_DOCUMENT.SEARCH_DOCUMENT_ID;

CREATE INDEX IF NOT EXISTS IDX_SEARCH_DOCUMENT_CONTENT ON product.SEARCH_DOCUMENT (CONTENT_ITEM_ID);
CREATE INDEX IF NOT EXISTS IDX_SEARCH_DOCUMENT_VECTOR ON product.SEARCH_DOCUMENT USING GIN (SEARCH_VECTOR);
