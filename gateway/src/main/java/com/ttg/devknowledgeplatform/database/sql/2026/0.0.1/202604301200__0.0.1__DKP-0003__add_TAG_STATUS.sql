-- liquibase formatted sql
-- changeset ttg:202604301200__0.0.1__DKP-0003__add_TAG_STATUS logicalFilePath:DevKnowledgePlatform
-- comment: Add STATUS to product.TAG (VARCHAR(10)) with check constraint matching TagStatus enum

ALTER TABLE product.TAG
    ADD COLUMN IF NOT EXISTS STATUS VARCHAR(10);

UPDATE product.TAG
SET STATUS = 'ACTIVE'
WHERE STATUS IS NULL;

ALTER TABLE product.TAG
    ALTER COLUMN STATUS SET NOT NULL;

ALTER TABLE product.TAG
    DROP CONSTRAINT IF EXISTS CKC_TAG_STATUS;

ALTER TABLE product.TAG
    ADD CONSTRAINT CKC_TAG_STATUS CHECK (STATUS IN ('ACTIVE', 'INACTIVE'));
