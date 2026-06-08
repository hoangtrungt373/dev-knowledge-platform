-- liquibase formatted sql
-- changeset ttg:202605080001__0.0.1__DKP-0004__fixes_and_article logicalFilePath:DevKnowledgePlatform
-- comment: Add USER_UUID to USER, audit columns to CONTENT_ITEM_TAG, unique constraint on CONTENT_ITEM_TAG, and ARTICLE table

-- =============================================================================
-- 1. Add USER_UUID to USER table (was missing from DKP-0002)
-- =============================================================================

ALTER TABLE product.USER
    ADD COLUMN IF NOT EXISTS USER_UUID VARCHAR(36);

UPDATE product.USER
SET USER_UUID = gen_random_uuid()::text
WHERE USER_UUID IS NULL;

ALTER TABLE product.USER
    ALTER COLUMN USER_UUID SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS UQ_USER_UUID ON product.USER (USER_UUID);

-- =============================================================================
-- 2. Add audit columns to CONTENT_ITEM_TAG (missing from DKP-0002)
-- =============================================================================

ALTER TABLE product.CONTENT_ITEM_TAG
    ADD COLUMN IF NOT EXISTS USR_CREATION VARCHAR(128);

ALTER TABLE product.CONTENT_ITEM_TAG
    ADD COLUMN IF NOT EXISTS DTE_CREATION TIMESTAMP WITH TIME ZONE;

ALTER TABLE product.CONTENT_ITEM_TAG
    ADD COLUMN IF NOT EXISTS USR_LAST_MODIFICATION VARCHAR(128);

ALTER TABLE product.CONTENT_ITEM_TAG
    ADD COLUMN IF NOT EXISTS DTE_LAST_MODIFICATION TIMESTAMP WITH TIME ZONE;

ALTER TABLE product.CONTENT_ITEM_TAG
    ADD COLUMN IF NOT EXISTS VERSION INTEGER;

UPDATE product.CONTENT_ITEM_TAG
SET USR_CREATION            = 'migration',
    DTE_CREATION            = NOW(),
    USR_LAST_MODIFICATION   = 'migration',
    DTE_LAST_MODIFICATION   = NOW(),
    VERSION                 = 0
WHERE USR_CREATION IS NULL;

ALTER TABLE product.CONTENT_ITEM_TAG ALTER COLUMN USR_CREATION          SET NOT NULL;
ALTER TABLE product.CONTENT_ITEM_TAG ALTER COLUMN DTE_CREATION          SET NOT NULL;
ALTER TABLE product.CONTENT_ITEM_TAG ALTER COLUMN USR_LAST_MODIFICATION SET NOT NULL;
ALTER TABLE product.CONTENT_ITEM_TAG ALTER COLUMN DTE_LAST_MODIFICATION SET NOT NULL;
ALTER TABLE product.CONTENT_ITEM_TAG ALTER COLUMN VERSION               SET NOT NULL;

-- =============================================================================
-- 3. Unique constraint on CONTENT_ITEM_TAG (CONTENT_ITEM_ID, TAG_ID)
-- =============================================================================

-- Remove duplicates first (keep the row with the lowest id)
DELETE FROM product.CONTENT_ITEM_TAG a
USING product.CONTENT_ITEM_TAG b
WHERE a.CONTENT_ITEM_TAG_ID > b.CONTENT_ITEM_TAG_ID
  AND a.CONTENT_ITEM_ID = b.CONTENT_ITEM_ID
  AND a.TAG_ID = b.TAG_ID;

ALTER TABLE product.CONTENT_ITEM_TAG
    DROP CONSTRAINT IF EXISTS UQ_CONTENT_ITEM_TAG;

ALTER TABLE product.CONTENT_ITEM_TAG
    ADD CONSTRAINT UQ_CONTENT_ITEM_TAG UNIQUE (CONTENT_ITEM_ID, TAG_ID);

-- =============================================================================
-- 4. ARTICLE table
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.ARTICLE_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.ARTICLE (
    ARTICLE_ID              INTEGER                         NOT NULL,
    CONTENT_ITEM_ID         INTEGER                         NOT NULL,
    BODY                    TEXT,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_ARTICLE PRIMARY KEY (ARTICLE_ID),

    CONSTRAINT FK_ARTICLE_CONTENT_ITEM FOREIGN KEY (CONTENT_ITEM_ID)
        REFERENCES product.CONTENT_ITEM (CONTENT_ITEM_ID)
);

ALTER SEQUENCE product.ARTICLE_SEQ OWNED BY product.ARTICLE.ARTICLE_ID;

CREATE UNIQUE INDEX IF NOT EXISTS UQ_ARTICLE_CONTENT_ITEM ON product.ARTICLE (CONTENT_ITEM_ID);
CREATE INDEX IF NOT EXISTS IDX_ARTICLE_CONTENT_ITEM ON product.ARTICLE (CONTENT_ITEM_ID);