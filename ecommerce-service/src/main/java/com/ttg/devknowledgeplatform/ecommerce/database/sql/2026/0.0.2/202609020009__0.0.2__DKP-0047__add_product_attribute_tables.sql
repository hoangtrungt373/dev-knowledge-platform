-- liquibase formatted sql
-- changeset ttg:202609020009__0.0.2__DKP-0047__add_product_attribute_tables logicalFilePath:EcommerceService
-- comment: Adds the "Option B" global attribute registry — PRODUCT_ATTRIBUTE (e.g. "Color", reusable across categories, mirrors PRODUCT_TAG's own flat name+slug-less simplicity), PRODUCT_ATTRIBUTE_VALUE (its controlled vocabulary, e.g. "Red"/"Blue"/"Black", cascade-owned by the attribute), and PRODUCT_CATEGORY_ATTRIBUTE (the many-to-many join declaring which attributes a PRODUCT_CATEGORY expects, with a per-assignment REQUIRED flag — an explicit join entity with audit columns, mirroring PRODUCT_TAG_ASSIGNMENT). ProductServiceImpl enforces this schema against ProductVariant.attributes once a category has at least one assignment; a category with none stays fully free-form, unchanged from today.

-- =============================================================================
-- PRODUCT_ATTRIBUTE
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ecommerce.PRODUCT_ATTRIBUTE_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS ecommerce.PRODUCT_ATTRIBUTE (
    PRODUCT_ATTRIBUTE_ID    INTEGER                         NOT NULL,
    NAME                    VARCHAR(50)                     NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_PRODUCT_ATTRIBUTE PRIMARY KEY (PRODUCT_ATTRIBUTE_ID)
);

ALTER SEQUENCE ecommerce.PRODUCT_ATTRIBUTE_SEQ OWNED BY ecommerce.PRODUCT_ATTRIBUTE.PRODUCT_ATTRIBUTE_ID;

-- Case-insensitive uniqueness (matches ProductAttributeServiceImpl's existsByNameIgnoreCase) —
-- not expressible as a plain column UNIQUE, which would be case-sensitive. Also what makes NAME
-- safe to match literally, case-sensitively, against a ProductVariant.attributes map key
-- elsewhere (see ProductAttribute's own Javadoc) — only one casing of any given name can ever exist.
CREATE UNIQUE INDEX IF NOT EXISTS UX_PRODUCT_ATTRIBUTE_NAME_LOWER ON ecommerce.PRODUCT_ATTRIBUTE (LOWER(NAME));

-- =============================================================================
-- PRODUCT_ATTRIBUTE_VALUE
-- Cascade-owned by PRODUCT_ATTRIBUTE (ProductAttribute.values, cascade ALL/orphanRemoval) — never
-- written independently. DISPLAY_ORDER is the value's position in the list the admin submitted,
-- not an independently-editable number (see ProductAttributeServiceImpl.applyValues).
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ecommerce.PRODUCT_ATTRIBUTE_VALUE_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS ecommerce.PRODUCT_ATTRIBUTE_VALUE (
    PRODUCT_ATTRIBUTE_VALUE_ID INTEGER                      NOT NULL,
    PRODUCT_ATTRIBUTE_ID       INTEGER                      NOT NULL,
    VALUE                      VARCHAR(50)                  NOT NULL,
    DISPLAY_ORDER              INTEGER                      NOT NULL,
    USR_CREATION               VARCHAR(128)                 NOT NULL,
    DTE_CREATION               TIMESTAMP WITH TIME ZONE     NOT NULL,
    USR_LAST_MODIFICATION      VARCHAR(128)                 NOT NULL,
    DTE_LAST_MODIFICATION      TIMESTAMP WITH TIME ZONE     NOT NULL,
    VERSION                    INTEGER                      NOT NULL,

    CONSTRAINT PK_PRODUCT_ATTRIBUTE_VALUE PRIMARY KEY (PRODUCT_ATTRIBUTE_VALUE_ID),
    CONSTRAINT FK_PRODUCT_ATTRIBUTE_VALUE_ATTRIBUTE FOREIGN KEY (PRODUCT_ATTRIBUTE_ID)
        REFERENCES ecommerce.PRODUCT_ATTRIBUTE (PRODUCT_ATTRIBUTE_ID)
);

ALTER SEQUENCE ecommerce.PRODUCT_ATTRIBUTE_VALUE_SEQ
    OWNED BY ecommerce.PRODUCT_ATTRIBUTE_VALUE.PRODUCT_ATTRIBUTE_VALUE_ID;

CREATE INDEX IF NOT EXISTS IDX_PRODUCT_ATTRIBUTE_VALUE_ATTRIBUTE ON ecommerce.PRODUCT_ATTRIBUTE_VALUE (PRODUCT_ATTRIBUTE_ID);

-- Case-insensitive uniqueness per attribute (e.g. can't add "Red" to "Color" twice) — matches
-- ProductAttributeServiceImpl.applyValues' own in-request duplicate check.
CREATE UNIQUE INDEX IF NOT EXISTS UX_PRODUCT_ATTRIBUTE_VALUE_ATTR_VALUE_LOWER
    ON ecommerce.PRODUCT_ATTRIBUTE_VALUE (PRODUCT_ATTRIBUTE_ID, LOWER(VALUE));

-- =============================================================================
-- PRODUCT_CATEGORY_ATTRIBUTE
-- Explicit join entity (not a bare @ManyToMany/@JoinTable) so the assignment row itself carries
-- audit columns, same reasoning as PRODUCT_TAG_ASSIGNMENT. Cascade-owned by PRODUCT_CATEGORY
-- (ProductCategory.categoryAttributes, cascade ALL/orphanRemoval). DISPLAY_ORDER is the
-- assignment's position in the list the admin submitted for that category, not independently
-- editable (see ProductCategoryServiceImpl.applyCategoryAttributes).
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ecommerce.PRODUCT_CATEGORY_ATTRIBUTE_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS ecommerce.PRODUCT_CATEGORY_ATTRIBUTE (
    PRODUCT_CATEGORY_ATTRIBUTE_ID INTEGER                     NOT NULL,
    PRODUCT_CATEGORY_ID           INTEGER                     NOT NULL,
    PRODUCT_ATTRIBUTE_ID          INTEGER                     NOT NULL,
    REQUIRED                      BOOLEAN                     NOT NULL DEFAULT FALSE,
    DISPLAY_ORDER                 INTEGER                     NOT NULL,
    USR_CREATION                  VARCHAR(128)                NOT NULL,
    DTE_CREATION                  TIMESTAMP WITH TIME ZONE    NOT NULL,
    USR_LAST_MODIFICATION         VARCHAR(128)                NOT NULL,
    DTE_LAST_MODIFICATION         TIMESTAMP WITH TIME ZONE    NOT NULL,
    VERSION                       INTEGER                     NOT NULL,

    CONSTRAINT PK_PRODUCT_CATEGORY_ATTRIBUTE PRIMARY KEY (PRODUCT_CATEGORY_ATTRIBUTE_ID),
    CONSTRAINT FK_PRODUCT_CATEGORY_ATTRIBUTE_CATEGORY FOREIGN KEY (PRODUCT_CATEGORY_ID)
        REFERENCES ecommerce.PRODUCT_CATEGORY (PRODUCT_CATEGORY_ID),
    CONSTRAINT FK_PRODUCT_CATEGORY_ATTRIBUTE_ATTRIBUTE FOREIGN KEY (PRODUCT_ATTRIBUTE_ID)
        REFERENCES ecommerce.PRODUCT_ATTRIBUTE (PRODUCT_ATTRIBUTE_ID)
);

ALTER SEQUENCE ecommerce.PRODUCT_CATEGORY_ATTRIBUTE_SEQ
    OWNED BY ecommerce.PRODUCT_CATEGORY_ATTRIBUTE.PRODUCT_CATEGORY_ATTRIBUTE_ID;

CREATE INDEX IF NOT EXISTS IDX_PRODUCT_CATEGORY_ATTRIBUTE_CATEGORY ON ecommerce.PRODUCT_CATEGORY_ATTRIBUTE (PRODUCT_CATEGORY_ID);
CREATE INDEX IF NOT EXISTS IDX_PRODUCT_CATEGORY_ATTRIBUTE_ATTRIBUTE ON ecommerce.PRODUCT_CATEGORY_ATTRIBUTE (PRODUCT_ATTRIBUTE_ID);

CREATE UNIQUE INDEX IF NOT EXISTS UX_PRODUCT_CATEGORY_ATTRIBUTE_PAIR
    ON ecommerce.PRODUCT_CATEGORY_ATTRIBUTE (PRODUCT_CATEGORY_ID, PRODUCT_ATTRIBUTE_ID);
ALTER TABLE ecommerce.PRODUCT_CATEGORY_ATTRIBUTE
    ADD CONSTRAINT UK_PRODUCT_CATEGORY_ATTRIBUTE_PAIR UNIQUE USING INDEX UX_PRODUCT_CATEGORY_ATTRIBUTE_PAIR;
