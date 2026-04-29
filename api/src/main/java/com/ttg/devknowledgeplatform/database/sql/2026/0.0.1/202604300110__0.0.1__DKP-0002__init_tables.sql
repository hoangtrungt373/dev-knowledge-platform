-- liquibase formatted sql
-- changeset ttg:202604300110__26.1.0__DKP-0002__init_tables logicalFilePath:DevKnowledgePlatform
-- comment: Initialize core product tables, constraints, and indexes

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

    CONSTRAINT FK_CATEGORY_PARENT FOREIGN KEY (PARENT_ID) REFERENCES product.CATEGORY (CATEGORY_ID)
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

    CONSTRAINT PK_TAG PRIMARY KEY (TAG_ID)
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

    CONSTRAINT CKC_CONTENT_ITEM_TYPE CHECK (TYPE IN ('INTERVIEW_QUESTION', 'ARTICLE', 'BLOG_POST')),
    CONSTRAINT CKC_CONTENT_ITEM_STATUS CHECK (STATUS IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

ALTER SEQUENCE product.CONTENT_ITEM_SEQ OWNED BY product.CONTENT_ITEM.CONTENT_ITEM_ID;

CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_SLUG ON product.CONTENT_ITEM (SLUG);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_TYPE ON product.CONTENT_ITEM (TYPE);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_STATUS ON product.CONTENT_ITEM (STATUS);
CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_CATEGORY ON product.CONTENT_ITEM (CATEGORY_ID);

-- =============================================================================
-- CONTENT_ITEM_TAG
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.CONTENT_ITEM_TAG_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.CONTENT_ITEM_TAG (
    CONTENT_ITEM_TAG_ID     INTEGER                         NOT NULL,
    CONTENT_ITEM_ID         INTEGER                         NOT NULL,
    TAG_ID                  INTEGER                         NOT NULL,

    CONSTRAINT PK_CONTENT_ITEM_TAG PRIMARY KEY (CONTENT_ITEM_TAG_ID),

    CONSTRAINT FK_CONTENT_ITEM_TAG_CONTENT FOREIGN KEY (CONTENT_ITEM_ID) REFERENCES product.CONTENT_ITEM (CONTENT_ITEM_ID),
    CONSTRAINT FK_CONTENT_ITEM_TAG_TAG FOREIGN KEY (TAG_ID) REFERENCES product.TAG (TAG_ID)
);

ALTER SEQUENCE product.CONTENT_ITEM_TAG_SEQ OWNED BY product.CONTENT_ITEM_TAG.CONTENT_ITEM_TAG_ID;

CREATE INDEX IF NOT EXISTS IDX_CONTENT_ITEM_TAG_CONTENT_ITEM ON product.CONTENT_ITEM_TAG (CONTENT_ITEM_ID);
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
    DIFFICULTY              VARCHAR(50)                     NOT NULL,
    QUESTION_BODY           TEXT                            NOT NULL,
    SHORT_ANSWER            TEXT,
    DETAILED_ANSWER         TEXT,
    IS_COMMON               BOOLEAN                         NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_INTERVIEW_QUESTION PRIMARY KEY (INTERVIEW_QUESTION_ID),

    CONSTRAINT FK_INTERVIEW_QUESTION_CONTENT FOREIGN KEY (CONTENT_ITEM_ID) REFERENCES product.CONTENT_ITEM (CONTENT_ITEM_ID),

    CONSTRAINT CKC_INTERVIEW_QUESTION_DIFFICULTY CHECK (DIFFICULTY IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED'))
);

ALTER SEQUENCE product.INTERVIEW_QUESTION_SEQ OWNED BY product.INTERVIEW_QUESTION.INTERVIEW_QUESTION_ID;

CREATE INDEX IF NOT EXISTS IDX_INTERVIEW_QUESTION_CONTENT ON product.INTERVIEW_QUESTION (CONTENT_ITEM_ID);
CREATE INDEX IF NOT EXISTS IDX_INTERVIEW_QUESTION_DIFFICULTY ON product.INTERVIEW_QUESTION (DIFFICULTY);