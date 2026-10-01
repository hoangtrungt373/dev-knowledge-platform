-- liquibase formatted sql
-- changeset ttg:202610010001__0.0.4__DKP-0055__add_problem_tag_tables logicalFilePath:DevPracticeService
-- comment: Problem tags (topics such as Array, Math, Stack) — PROBLEM_TAG (flat name+slug catalog) and an explicit PROBLEM_TAG_ASSIGNMENT join entity, mirroring ecommerce-service's PRODUCT_TAG/PRODUCT_TAG_ASSIGNMENT; seeds the NeetCode topic list.

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

-- =============================================================================
-- Seed: the NeetCode topic list, so a fresh database has a usable catalog. Admins can rename or
-- delete any of these like any other tag.
--
-- Each row takes its id from nextval(). The entity side allocates ids with Hibernate's pooled-lo
-- optimizer (INCREMENT BY 50: one nextval() reserves the block [v, v+49]), so a raw nextval() here
-- consumes a whole block — ids come out as 1, 51, 101, ... — and Hibernate's own later blocks can
-- never overlap them. Hardcoded ids would instead collide with Hibernate's first block.
-- Slugs match what SlugService would generate for each name.
-- =============================================================================

INSERT INTO dev_practice.PROBLEM_TAG
    (PROBLEM_TAG_ID, NAME, SLUG, USR_CREATION, DTE_CREATION, USR_LAST_MODIFICATION, DTE_LAST_MODIFICATION, VERSION)
SELECT nextval('dev_practice.PROBLEM_TAG_SEQ'), t.name, t.slug, 'liquibase', now(), 'liquibase', now(), 0
FROM (VALUES
    (1,  'Array',               'array'),
    (2,  'String',              'string'),
    (3,  'Hash Table',          'hash-table'),
    (4,  'Two Pointers',        'two-pointers'),
    (5,  'Sliding Window',      'sliding-window'),
    (6,  'Stack',               'stack'),
    (7,  'Binary Search',       'binary-search'),
    (8,  'Linked List',         'linked-list'),
    (9,  'Tree',                'tree'),
    (10, 'Trie',                'trie'),
    (11, 'Heap',                'heap'),
    (12, 'Backtracking',        'backtracking'),
    (13, 'Graph',               'graph'),
    (14, 'Dynamic Programming', 'dynamic-programming'),
    (15, 'Greedy',              'greedy'),
    (16, 'Intervals',           'intervals'),
    (17, 'Math',                'math'),
    (18, 'Bit Manipulation',    'bit-manipulation')
) AS t(ord, name, slug)
ORDER BY t.ord;
