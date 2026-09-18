-- liquibase formatted sql
-- changeset ttg:202609180001__0.0.4__DKP-0053__add_dev_practice_method_signature_and_judging_columns logicalFilePath:DevPracticeService
-- comment: Phase 2 — add PROBLEM's method-signature columns, the new METHOD_PARAMETER table, and SUBMISSION's judging-result columns
--
-- Additive-only, per this reactor's never-edit-an-already-run-changeset convention — DKP-0052
-- stays untouched. Phase 1's PROBLEM/SUBMISSION rows (should any already exist) get NOT NULL
-- METHOD_NAME/RETURN_TYPE backfilled via a placeholder default before the constraint is applied,
-- since this module had no method-signature concept at all until this changeset.
--
-- METHOD_PARAMETER mirrors TEST_CASE's own shape (ON DELETE CASCADE from PROBLEM — a parameter
-- never outlives its problem) but orders by POSITION, not insertion id, since a signature's
-- parameter order is semantically load-bearing (see the entity's own Javadoc).
--
-- SUBMISSION's three new columns are all nullable — Phase 1 submissions (and any submission whose
-- judging run hasn't finished yet) simply have them unset, never a placeholder zero/empty string.

ALTER TABLE dev_practice.PROBLEM
    ADD COLUMN IF NOT EXISTS METHOD_NAME VARCHAR(100) NOT NULL DEFAULT 'solve',
    ADD COLUMN IF NOT EXISTS RETURN_TYPE VARCHAR(50)  NOT NULL DEFAULT 'STRING';

ALTER TABLE dev_practice.PROBLEM
    ALTER COLUMN METHOD_NAME DROP DEFAULT,
    ALTER COLUMN RETURN_TYPE DROP DEFAULT;

ALTER TABLE dev_practice.PROBLEM
    ADD CONSTRAINT CKC_PROBLEM_RETURN_TYPE CHECK (RETURN_TYPE IN
        ('INT','LONG','DOUBLE','BOOLEAN','STRING','INT_ARRAY','DOUBLE_ARRAY','BOOLEAN_ARRAY','STRING_ARRAY','INT_MATRIX'));

CREATE SEQUENCE IF NOT EXISTS dev_practice.METHOD_PARAMETER_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS dev_practice.METHOD_PARAMETER (
    METHOD_PARAMETER_ID     INTEGER                         NOT NULL,
    PROBLEM_ID              INTEGER                         NOT NULL,
    NAME                    VARCHAR(100)                    NOT NULL,
    TYPE                    VARCHAR(50)                     NOT NULL,
    POSITION                INTEGER                         NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_METHOD_PARAMETER PRIMARY KEY (METHOD_PARAMETER_ID),
    CONSTRAINT FK_METHOD_PARAMETER_PROBLEM FOREIGN KEY (PROBLEM_ID)
        REFERENCES dev_practice.PROBLEM (PROBLEM_ID) ON DELETE CASCADE,
    CONSTRAINT CKC_METHOD_PARAMETER_TYPE CHECK (TYPE IN
        ('INT','LONG','DOUBLE','BOOLEAN','STRING','INT_ARRAY','DOUBLE_ARRAY','BOOLEAN_ARRAY','STRING_ARRAY','INT_MATRIX'))
);

ALTER SEQUENCE dev_practice.METHOD_PARAMETER_SEQ OWNED BY dev_practice.METHOD_PARAMETER.METHOD_PARAMETER_ID;

CREATE INDEX IF NOT EXISTS IDX_METHOD_PARAMETER_PROBLEM ON dev_practice.METHOD_PARAMETER (PROBLEM_ID);

ALTER TABLE dev_practice.SUBMISSION
    ADD COLUMN IF NOT EXISTS PASSED_TEST_CASES INTEGER,
    ADD COLUMN IF NOT EXISTS TOTAL_TEST_CASES  INTEGER,
    ADD COLUMN IF NOT EXISTS ERROR_MESSAGE     TEXT;
