-- liquibase formatted sql
-- changeset ttg:202608310001__0.0.2__DKP-0037__add_product_category_parent_id logicalFilePath:EcommerceService
-- comment: Adds parent/child hierarchy support to PRODUCT_CATEGORY (self-referential adjacency list, mirroring content-service's CATEGORY.PARENT_ID) — nullable, since most rows stay root-level and every category created before this migration must remain valid with no parent

ALTER TABLE ecommerce.PRODUCT_CATEGORY
    ADD COLUMN IF NOT EXISTS PARENT_CATEGORY_ID INTEGER;

ALTER TABLE ecommerce.PRODUCT_CATEGORY
    ADD CONSTRAINT FK_PRODUCT_CATEGORY_PARENT FOREIGN KEY (PARENT_CATEGORY_ID)
        REFERENCES ecommerce.PRODUCT_CATEGORY (PRODUCT_CATEGORY_ID);

CREATE INDEX IF NOT EXISTS IDX_PRODUCT_CATEGORY_PARENT ON ecommerce.PRODUCT_CATEGORY (PARENT_CATEGORY_ID);
