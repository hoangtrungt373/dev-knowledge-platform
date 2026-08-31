-- liquibase formatted sql
-- changeset ttg:202609010001__0.0.2__DKP-0038__add_product_tag_tables logicalFilePath:EcommerceService
-- comment: Adds Product Tag support (many-to-many, a product can have multiple tags and a tag can be attached to multiple products) — PRODUCT_TAG (flat, name+slug only, no status/seedId — mirrors ProductCategory's own pre-hierarchy simplicity, not content-service's Tag) and an explicit join entity PRODUCT_TAG_ASSIGNMENT (not a bare join table) so the assignment row itself carries the same audit columns every other entity in this reactor does, mirroring content-service's ContentItemTag

-- =============================================================================
-- PRODUCT_TAG
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ecommerce.PRODUCT_TAG_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS ecommerce.PRODUCT_TAG (
    PRODUCT_TAG_ID          INTEGER                         NOT NULL,
    NAME                    VARCHAR(100)                    NOT NULL,
    SLUG                    VARCHAR(100)                    NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_PRODUCT_TAG PRIMARY KEY (PRODUCT_TAG_ID),
    CONSTRAINT UK_PRODUCT_TAG_SLUG UNIQUE (SLUG)
);

ALTER SEQUENCE ecommerce.PRODUCT_TAG_SEQ OWNED BY ecommerce.PRODUCT_TAG.PRODUCT_TAG_ID;

-- Case-insensitive uniqueness (matches ProductTagServiceImpl's existsByNameIgnoreCase) — not
-- expressible as a plain column UNIQUE, which would be case-sensitive.
CREATE UNIQUE INDEX IF NOT EXISTS UX_PRODUCT_TAG_NAME_LOWER ON ecommerce.PRODUCT_TAG (LOWER(NAME));

-- =============================================================================
-- PRODUCT_TAG_ASSIGNMENT
-- Explicit join entity (not a bare @ManyToMany/@JoinTable) so the assignment row itself carries
-- audit columns, same reasoning as content-service's CONTENT_ITEM_TAG.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ecommerce.PRODUCT_TAG_ASSIGNMENT_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS ecommerce.PRODUCT_TAG_ASSIGNMENT (
    PRODUCT_TAG_ASSIGNMENT_ID  INTEGER                         NOT NULL,
    PRODUCT_ID                 INTEGER                         NOT NULL,
    PRODUCT_TAG_ID              INTEGER                         NOT NULL,
    USR_CREATION                VARCHAR(128)                    NOT NULL,
    DTE_CREATION                TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION       VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION       TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                     INTEGER                         NOT NULL,

    CONSTRAINT PK_PRODUCT_TAG_ASSIGNMENT PRIMARY KEY (PRODUCT_TAG_ASSIGNMENT_ID),
    CONSTRAINT FK_PRODUCT_TAG_ASSIGNMENT_PRODUCT FOREIGN KEY (PRODUCT_ID)
        REFERENCES ecommerce.PRODUCT (PRODUCT_ID),
    CONSTRAINT FK_PRODUCT_TAG_ASSIGNMENT_TAG FOREIGN KEY (PRODUCT_TAG_ID)
        REFERENCES ecommerce.PRODUCT_TAG (PRODUCT_TAG_ID)
);

ALTER SEQUENCE ecommerce.PRODUCT_TAG_ASSIGNMENT_SEQ
    OWNED BY ecommerce.PRODUCT_TAG_ASSIGNMENT.PRODUCT_TAG_ASSIGNMENT_ID;

CREATE INDEX IF NOT EXISTS IDX_PRODUCT_TAG_ASSIGNMENT_PRODUCT ON ecommerce.PRODUCT_TAG_ASSIGNMENT (PRODUCT_ID);
CREATE INDEX IF NOT EXISTS IDX_PRODUCT_TAG_ASSIGNMENT_TAG ON ecommerce.PRODUCT_TAG_ASSIGNMENT (PRODUCT_TAG_ID);

CREATE UNIQUE INDEX IF NOT EXISTS UX_PRODUCT_TAG_ASSIGNMENT_PAIR
    ON ecommerce.PRODUCT_TAG_ASSIGNMENT (PRODUCT_ID, PRODUCT_TAG_ID);
ALTER TABLE ecommerce.PRODUCT_TAG_ASSIGNMENT
    ADD CONSTRAINT UK_PRODUCT_TAG_ASSIGNMENT_PAIR UNIQUE USING INDEX UX_PRODUCT_TAG_ASSIGNMENT_PAIR;
