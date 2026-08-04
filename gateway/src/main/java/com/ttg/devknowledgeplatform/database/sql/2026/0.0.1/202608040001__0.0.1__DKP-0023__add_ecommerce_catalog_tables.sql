-- liquibase formatted sql
-- changeset ttg:202608040001__0.0.1__DKP-0023__add_ecommerce_catalog_tables logicalFilePath:DevKnowledgePlatform
-- comment: Add ecommerce-service catalog tables (Epic 1: Catalog & Search) — categories, products, variants, images, the CQRS search read model, and the shared transactional outbox

-- =============================================================================
-- pg_trgm extension — trigram similarity for typo-tolerant search on
-- PRODUCT_SEARCH_VIEW.SEARCH_TEXT, complementing exact-token tsvector matching.
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =============================================================================
-- PRODUCT_CATEGORY
-- Flat product taxonomy. Distinct from CATEGORY (content-service's knowledge-base
-- taxonomy) — same schema, unrelated domain, deliberately different table name.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.PRODUCT_CATEGORY_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.PRODUCT_CATEGORY (
    PRODUCT_CATEGORY_ID     INTEGER                         NOT NULL,
    NAME                    VARCHAR(100)                    NOT NULL,
    SLUG                    VARCHAR(100)                    NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_PRODUCT_CATEGORY PRIMARY KEY (PRODUCT_CATEGORY_ID),
    CONSTRAINT UK_PRODUCT_CATEGORY_SLUG UNIQUE (SLUG)
);

ALTER SEQUENCE product.PRODUCT_CATEGORY_SEQ OWNED BY product.PRODUCT_CATEGORY.PRODUCT_CATEGORY_ID;

-- =============================================================================
-- PRODUCT
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.PRODUCT_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.PRODUCT (
    PRODUCT_ID              INTEGER                         NOT NULL,
    NAME                    VARCHAR(150)                    NOT NULL,
    DESCRIPTION             TEXT,
    SLUG                    VARCHAR(150)                    NOT NULL,
    ACTIVE                  BOOLEAN                         NOT NULL DEFAULT TRUE,
    PRODUCT_CATEGORY_ID     INTEGER                         NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_PRODUCT PRIMARY KEY (PRODUCT_ID),
    CONSTRAINT UK_PRODUCT_SLUG UNIQUE (SLUG),
    CONSTRAINT FK_PRODUCT_PRODUCT_CATEGORY FOREIGN KEY (PRODUCT_CATEGORY_ID)
        REFERENCES product.PRODUCT_CATEGORY (PRODUCT_CATEGORY_ID)
);

ALTER SEQUENCE product.PRODUCT_SEQ OWNED BY product.PRODUCT.PRODUCT_ID;

CREATE INDEX IF NOT EXISTS IDX_PRODUCT_PRODUCT_CATEGORY ON product.PRODUCT (PRODUCT_CATEGORY_ID);
CREATE INDEX IF NOT EXISTS IDX_PRODUCT_ACTIVE            ON product.PRODUCT (ACTIVE);

-- =============================================================================
-- PRODUCT_IMAGE
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.PRODUCT_IMAGE_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.PRODUCT_IMAGE (
    PRODUCT_IMAGE_ID         INTEGER                        NOT NULL,
    PRODUCT_ID               INTEGER                        NOT NULL,
    STORAGE_KEY              VARCHAR(255)                   NOT NULL,
    SORT_ORDER               INTEGER                        NOT NULL,
    USR_CREATION             VARCHAR(128)                   NOT NULL,
    DTE_CREATION             TIMESTAMP WITH TIME ZONE       NOT NULL,
    USR_LAST_MODIFICATION    VARCHAR(128)                   NOT NULL,
    DTE_LAST_MODIFICATION    TIMESTAMP WITH TIME ZONE       NOT NULL,
    VERSION                  INTEGER                        NOT NULL,

    CONSTRAINT PK_PRODUCT_IMAGE PRIMARY KEY (PRODUCT_IMAGE_ID),
    CONSTRAINT UK_PRODUCT_IMAGE_PRODUCT_SORT_ORDER UNIQUE (PRODUCT_ID, SORT_ORDER),
    CONSTRAINT FK_PRODUCT_IMAGE_PRODUCT FOREIGN KEY (PRODUCT_ID)
        REFERENCES product.PRODUCT (PRODUCT_ID)
);

ALTER SEQUENCE product.PRODUCT_IMAGE_SEQ OWNED BY product.PRODUCT_IMAGE.PRODUCT_IMAGE_ID;

CREATE INDEX IF NOT EXISTS IDX_PRODUCT_IMAGE_PRODUCT ON product.PRODUCT_IMAGE (PRODUCT_ID);

-- =============================================================================
-- PRODUCT_VARIANT
-- RESERVED_QUANTITY <= STOCK_QUANTITY is enforced here as defense in depth —
-- application logic (Epic 3's reservation saga) is the primary guard, but a CHECK
-- constraint means a bug there can't silently oversell.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.PRODUCT_VARIANT_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.PRODUCT_VARIANT (
    PRODUCT_VARIANT_ID        INTEGER                       NOT NULL,
    PRODUCT_ID                 INTEGER                      NOT NULL,
    SKU                         VARCHAR(64)                 NOT NULL,
    PRICE                       NUMERIC(12,2)                NOT NULL,
    STOCK_QUANTITY              INTEGER                      NOT NULL DEFAULT 0,
    RESERVED_QUANTITY           INTEGER                      NOT NULL DEFAULT 0,
    ATTRIBUTES                  JSONB                        NOT NULL DEFAULT '{}',
    USR_CREATION                VARCHAR(128)                 NOT NULL,
    DTE_CREATION                TIMESTAMP WITH TIME ZONE     NOT NULL,
    USR_LAST_MODIFICATION       VARCHAR(128)                 NOT NULL,
    DTE_LAST_MODIFICATION       TIMESTAMP WITH TIME ZONE     NOT NULL,
    VERSION                     INTEGER                      NOT NULL,

    CONSTRAINT PK_PRODUCT_VARIANT PRIMARY KEY (PRODUCT_VARIANT_ID),
    CONSTRAINT UK_PRODUCT_VARIANT_SKU UNIQUE (SKU),
    CONSTRAINT FK_PRODUCT_VARIANT_PRODUCT FOREIGN KEY (PRODUCT_ID)
        REFERENCES product.PRODUCT (PRODUCT_ID),
    CONSTRAINT CKC_PRODUCT_VARIANT_STOCK_NON_NEGATIVE CHECK (STOCK_QUANTITY >= 0),
    CONSTRAINT CKC_PRODUCT_VARIANT_RESERVED_WITHIN_STOCK
        CHECK (RESERVED_QUANTITY >= 0 AND RESERVED_QUANTITY <= STOCK_QUANTITY)
);

ALTER SEQUENCE product.PRODUCT_VARIANT_SEQ OWNED BY product.PRODUCT_VARIANT.PRODUCT_VARIANT_ID;

CREATE INDEX IF NOT EXISTS IDX_PRODUCT_VARIANT_PRODUCT ON product.PRODUCT_VARIANT (PRODUCT_ID);

-- GIN index for JSONB containment queries (e.g. ATTRIBUTES @> '{"size":"M"}'), used for
-- admin/service-layer attribute lookups directly against the write side.
CREATE INDEX IF NOT EXISTS IDX_PRODUCT_VARIANT_ATTRIBUTES ON product.PRODUCT_VARIANT USING gin (ATTRIBUTES);

-- =============================================================================
-- PRODUCT_SEARCH_VIEW
-- CQRS read model for browse/search/filter — the only table those endpoints query.
-- Written exclusively by the outbox-driven projection relay (US-1.5).
--
-- SEARCH_VECTOR is a STORED generated column derived from SEARCH_TEXT: Postgres keeps
-- it in sync automatically on every insert/update, so no application code (and no
-- Hibernate custom JdbcType, unlike the VECTOR column in CONTENT_EMBEDDING) ever needs
-- to write to it — it's read-only from the JPA entity's point of view, which is why it
-- isn't mapped as a Java field at all. The two-argument to_tsvector(regconfig, text)
-- form is IMMUTABLE (unlike the one-argument form, which depends on the mutable
-- default_text_search_config GUC), which is what makes it legal in a generated column.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.PRODUCT_SEARCH_VIEW_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.PRODUCT_SEARCH_VIEW (
    PRODUCT_SEARCH_VIEW_ID     INTEGER                       NOT NULL,
    PRODUCT_ID                  INTEGER                      NOT NULL,
    NAME                        VARCHAR(150)                 NOT NULL,
    SLUG                        VARCHAR(150)                 NOT NULL,
    PRODUCT_CATEGORY_ID         INTEGER                      NOT NULL,
    CATEGORY_NAME               VARCHAR(100)                 NOT NULL,
    MIN_PRICE                   NUMERIC(12,2)                 NOT NULL,
    MAX_PRICE                   NUMERIC(12,2)                 NOT NULL,
    IN_STOCK                    BOOLEAN                       NOT NULL DEFAULT FALSE,
    SEARCH_TEXT                 TEXT                          NOT NULL,
    SEARCH_VECTOR                TSVECTOR GENERATED ALWAYS AS (to_tsvector('english', SEARCH_TEXT)) STORED,
    AVAILABLE_ATTRIBUTES         JSONB                         NOT NULL DEFAULT '{}',
    USR_CREATION                 VARCHAR(128)                  NOT NULL,
    DTE_CREATION                 TIMESTAMP WITH TIME ZONE      NOT NULL,
    USR_LAST_MODIFICATION        VARCHAR(128)                  NOT NULL,
    DTE_LAST_MODIFICATION        TIMESTAMP WITH TIME ZONE      NOT NULL,
    VERSION                      INTEGER                       NOT NULL,

    CONSTRAINT PK_PRODUCT_SEARCH_VIEW PRIMARY KEY (PRODUCT_SEARCH_VIEW_ID),
    CONSTRAINT UK_PRODUCT_SEARCH_VIEW_PRODUCT UNIQUE (PRODUCT_ID),
    CONSTRAINT FK_PRODUCT_SEARCH_VIEW_PRODUCT FOREIGN KEY (PRODUCT_ID)
        REFERENCES product.PRODUCT (PRODUCT_ID)
);

ALTER SEQUENCE product.PRODUCT_SEARCH_VIEW_SEQ OWNED BY product.PRODUCT_SEARCH_VIEW.PRODUCT_SEARCH_VIEW_ID;

CREATE INDEX IF NOT EXISTS IDX_PRODUCT_SEARCH_VIEW_CATEGORY ON product.PRODUCT_SEARCH_VIEW (PRODUCT_CATEGORY_ID);

-- Full-text relevance ranking (ts_rank against this).
CREATE INDEX IF NOT EXISTS IDX_PRODUCT_SEARCH_VIEW_VECTOR ON product.PRODUCT_SEARCH_VIEW USING gin (SEARCH_VECTOR);

-- Trigram similarity for typo-tolerant matching — catches near-misses tsvector's exact
-- token matching would miss (e.g. "sneaekr" still finding "sneaker").
CREATE INDEX IF NOT EXISTS IDX_PRODUCT_SEARCH_VIEW_TRGM ON product.PRODUCT_SEARCH_VIEW USING gin (SEARCH_TEXT gin_trgm_ops);

-- JSONB containment for attribute filters (e.g. AVAILABLE_ATTRIBUTES @> '{"size":["M"]}').
CREATE INDEX IF NOT EXISTS IDX_PRODUCT_SEARCH_VIEW_ATTRIBUTES ON product.PRODUCT_SEARCH_VIEW USING gin (AVAILABLE_ATTRIBUTES);

-- =============================================================================
-- OUTBOX_EVENT
-- Shared transactional-outbox table for every ecommerce-service epic (catalog
-- read-model sync now; payment/webhook events and embedding re-index later).
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.OUTBOX_EVENT_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.OUTBOX_EVENT (
    OUTBOX_EVENT_ID          INTEGER                        NOT NULL,
    EVENT_TYPE               VARCHAR(100)                   NOT NULL,
    AGGREGATE_TYPE           VARCHAR(100)                   NOT NULL,
    AGGREGATE_ID             INTEGER                        NOT NULL,
    PAYLOAD                  JSONB                          NOT NULL,
    STATUS                   VARCHAR(20)                    NOT NULL DEFAULT 'PENDING',
    ATTEMPT_COUNT            INTEGER                        NOT NULL DEFAULT 0,
    LAST_ERROR               TEXT,
    PROCESSED_AT             TIMESTAMP WITH TIME ZONE,
    USR_CREATION             VARCHAR(128)                   NOT NULL,
    DTE_CREATION             TIMESTAMP WITH TIME ZONE       NOT NULL,
    USR_LAST_MODIFICATION    VARCHAR(128)                   NOT NULL,
    DTE_LAST_MODIFICATION    TIMESTAMP WITH TIME ZONE       NOT NULL,
    VERSION                  INTEGER                        NOT NULL,

    CONSTRAINT PK_OUTBOX_EVENT PRIMARY KEY (OUTBOX_EVENT_ID),
    CONSTRAINT CKC_OUTBOX_EVENT_STATUS
        CHECK (STATUS IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    -- AGGREGATE_TYPE is a small, slow-growing set (roughly one new value per future epic),
    -- unlike EVENT_TYPE below, which every epic's own business events keep adding to — that
    -- asymmetry is why only this one gets a CHECK. Widen this list in a new migration
    -- whenever a later epic (orders, payments, reviews) introduces its own aggregate root;
    -- see OutboxAggregateType's Javadoc.
    CONSTRAINT CKC_OUTBOX_EVENT_AGGREGATE_TYPE CHECK (AGGREGATE_TYPE IN ('PRODUCT')),
    CONSTRAINT CKC_OUTBOX_EVENT_ATTEMPT_COUNT_NON_NEGATIVE CHECK (ATTEMPT_COUNT >= 0)
);

ALTER SEQUENCE product.OUTBOX_EVENT_SEQ OWNED BY product.OUTBOX_EVENT.OUTBOX_EVENT_ID;

-- Partial index: the relay only ever queries WHERE STATUS = 'PENDING' (to claim the next
-- batch to dispatch), so only that small tail needs to be indexed, not the whole
-- ever-growing table. STATUS itself doesn't need to be in the index columns — every row
-- in this partial index already satisfies the predicate — just the PK for ordered fetch.
CREATE INDEX IF NOT EXISTS IDX_OUTBOX_EVENT_PENDING ON product.OUTBOX_EVENT (OUTBOX_EVENT_ID) WHERE STATUS = 'PENDING';
