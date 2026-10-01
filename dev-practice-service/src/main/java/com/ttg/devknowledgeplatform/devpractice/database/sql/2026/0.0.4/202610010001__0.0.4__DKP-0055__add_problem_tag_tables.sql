-- liquibase formatted sql
-- changeset ttg:202610010001__0.0.4__DKP-0055__add_problem_tag_tables logicalFilePath:DevPracticeService
-- comment: Problem tags (topics such as Array, Math, Stack) — PROBLEM_TAG (flat name+slug catalog) and an explicit PROBLEM_TAG_ASSIGNMENT join entity, mirroring ecommerce-service's PRODUCT_TAG/PRODUCT_TAG_ASSIGNMENT. The starter topic list is seeded by service.seed.ProblemTagSeeder (data/csv/problem_tags.csv), not here.

-- =============================================================================
-- PROBLEM_TAG
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS dev_practice.PROBLEM_TAG_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS dev_practice.PROBLEM_TAG (
    PROBLEM_TAG_ID          INTEGER                         NOT NULL,
    NAME                    VARCHAR(100)                    NOT NULL,
    SLUG                    VARCHAR(100)                    NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_PROBLEM_TAG PRIMARY KEY (PROBLEM_TAG_ID),
    CONSTRAINT UK_PROBLEM_TAG_SLUG UNIQUE (SLUG)
);

ALTER SEQUENCE dev_practice.PROBLEM_TAG_SEQ OWNED BY dev_practice.PROBLEM_TAG.PROBLEM_TAG_ID;

-- Case-insensitive name uniqueness ("Array" and "array" are the same tag), matching
-- ProblemTagServiceImpl's existsByNameIgnoreCase. A functional index on LOWER(NAME) is the way to
-- say that in Postgres — a plain UNIQUE (NAME) constraint is case-sensitive. It also makes the
-- service's own case-insensitive lookup index-backed.
CREATE UNIQUE INDEX IF NOT EXISTS UX_PROBLEM_TAG_NAME_LOWER ON dev_practice.PROBLEM_TAG (LOWER(NAME));

-- =============================================================================
-- PROBLEM_TAG_ASSIGNMENT
-- An explicit join entity rather than a bare join table, so each assignment carries the audit
-- columns every other row in this reactor has (same reasoning as PRODUCT_TAG_ASSIGNMENT).
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS dev_practice.PROBLEM_TAG_ASSIGNMENT_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS dev_practice.PROBLEM_TAG_ASSIGNMENT (
    PROBLEM_TAG_ASSIGNMENT_ID   INTEGER                         NOT NULL,
    PROBLEM_ID                  INTEGER                         NOT NULL,
    PROBLEM_TAG_ID              INTEGER                         NOT NULL,
    USR_CREATION                VARCHAR(128)                    NOT NULL,
    DTE_CREATION                TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION       VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION       TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                     INTEGER                         NOT NULL,

    CONSTRAINT PK_PROBLEM_TAG_ASSIGNMENT PRIMARY KEY (PROBLEM_TAG_ASSIGNMENT_ID),
    -- CASCADE from PROBLEM: assignments are part of the problem (like its test cases), so deleting
    -- a problem takes its tag links with it. Deliberately NOT cascading from PROBLEM_TAG — deleting
    -- a tag that's still attached is refused by the service (PROBLEM_TAG_IN_USE) instead.
    CONSTRAINT FK_PROBLEM_TAG_ASSIGNMENT_PROBLEM FOREIGN KEY (PROBLEM_ID)
        REFERENCES dev_practice.PROBLEM (PROBLEM_ID) ON DELETE CASCADE,
    CONSTRAINT FK_PROBLEM_TAG_ASSIGNMENT_TAG FOREIGN KEY (PROBLEM_TAG_ID)
        REFERENCES dev_practice.PROBLEM_TAG (PROBLEM_TAG_ID),
    CONSTRAINT UK_PROBLEM_TAG_ASSIGNMENT_PAIR UNIQUE (PROBLEM_ID, PROBLEM_TAG_ID)
);

ALTER SEQUENCE dev_practice.PROBLEM_TAG_ASSIGNMENT_SEQ
    OWNED BY dev_practice.PROBLEM_TAG_ASSIGNMENT.PROBLEM_TAG_ASSIGNMENT_ID;

-- The (PROBLEM_ID, PROBLEM_TAG_ID) unique constraint's own index already serves lookups by
-- PROBLEM_ID (its leading column) — loading a problem's tags. Only the reverse direction needs its
-- own index: the tag filter (WHERE PROBLEM_TAG_ID IN (...)) and the delete-in-use check.
CREATE INDEX IF NOT EXISTS IDX_PROBLEM_TAG_ASSIGNMENT_TAG ON dev_practice.PROBLEM_TAG_ASSIGNMENT (PROBLEM_TAG_ID);

