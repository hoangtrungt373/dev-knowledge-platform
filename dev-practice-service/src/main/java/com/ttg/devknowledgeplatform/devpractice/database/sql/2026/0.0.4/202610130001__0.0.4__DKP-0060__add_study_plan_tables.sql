-- liquibase formatted sql
-- changeset ttg:202610130001__0.0.4__DKP-0060__add_study_plan_tables logicalFilePath:DevPracticeService
-- comment: Study plans — curated, ordered problem lists such as "NeetCode 150". Three levels: STUDY_PLAN -> ordered STUDY_PLAN_SECTION (a free-text chapter: a topic, a week, a day) -> ordered STUDY_PLAN_PROBLEM (an explicit join to PROBLEM). Plans are seeded by service.seed.StudyPlanSeeder (data/study-plans/*.yml), not here.

-- =============================================================================
-- STUDY_PLAN
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS dev_practice.STUDY_PLAN_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS dev_practice.STUDY_PLAN (
    STUDY_PLAN_ID           INTEGER                         NOT NULL,
    NAME                    VARCHAR(150)                    NOT NULL,
    SLUG                    VARCHAR(150)                    NOT NULL,
    -- TEXT, not VARCHAR(n): a plan's blurb is free prose with no natural length cap worth enforcing.
    DESCRIPTION             TEXT,
    -- Display order on the plans overview. Not unique: two plans sharing a position simply fall back
    -- to name order, which is harmless — a uniqueness rule here would only make reordering awkward.
    POSITION                INTEGER                         NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_STUDY_PLAN PRIMARY KEY (STUDY_PLAN_ID),
    CONSTRAINT UK_STUDY_PLAN_SLUG UNIQUE (SLUG)
);

ALTER SEQUENCE dev_practice.STUDY_PLAN_SEQ OWNED BY dev_practice.STUDY_PLAN.STUDY_PLAN_ID;

-- =============================================================================
-- STUDY_PLAN_SECTION
-- A generic chapter (title + position), deliberately NOT a link to PROBLEM_TAG: real plans group by
-- topic (NeetCode 150), by week (Grind 75) or by day ("30 Days of ..."), and only a free-text title
-- covers all three.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS dev_practice.STUDY_PLAN_SECTION_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS dev_practice.STUDY_PLAN_SECTION (
    STUDY_PLAN_SECTION_ID   INTEGER                         NOT NULL,
    STUDY_PLAN_ID           INTEGER                         NOT NULL,
    TITLE                   VARCHAR(150)                    NOT NULL,
    DESCRIPTION             TEXT,
    -- No UNIQUE (STUDY_PLAN_ID, POSITION): a future admin "replace all sections" save goes through
    -- orphanRemoval, and Hibernate flushes inserts before orphan deletes — a uniqueness constraint
    -- would make a reorder collide with the rows it is replacing. The writer assigns 0..n-1 instead.
    POSITION                INTEGER                         NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_STUDY_PLAN_SECTION PRIMARY KEY (STUDY_PLAN_SECTION_ID),
    -- CASCADE: a section is part of its plan, the way test cases are part of a problem.
    CONSTRAINT FK_STUDY_PLAN_SECTION_PLAN FOREIGN KEY (STUDY_PLAN_ID)
        REFERENCES dev_practice.STUDY_PLAN (STUDY_PLAN_ID) ON DELETE CASCADE
);

ALTER SEQUENCE dev_practice.STUDY_PLAN_SECTION_SEQ
    OWNED BY dev_practice.STUDY_PLAN_SECTION.STUDY_PLAN_SECTION_ID;

-- Loading a plan's sections (the only access path) filters on STUDY_PLAN_ID; Postgres does not index
-- a foreign key's referencing column on its own.
CREATE INDEX IF NOT EXISTS IDX_STUDY_PLAN_SECTION_PLAN ON dev_practice.STUDY_PLAN_SECTION (STUDY_PLAN_ID);

-- =============================================================================
-- STUDY_PLAN_PROBLEM
-- An explicit join entity (audit columns, plus its own POSITION) rather than a bare join table — same
-- reasoning as PROBLEM_TAG_ASSIGNMENT. A problem may appear in many plans (NeetCode 150 and Blind 75
-- overlap heavily), each with its own order.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS dev_practice.STUDY_PLAN_PROBLEM_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS dev_practice.STUDY_PLAN_PROBLEM (
    STUDY_PLAN_PROBLEM_ID   INTEGER                         NOT NULL,
    STUDY_PLAN_SECTION_ID   INTEGER                         NOT NULL,
    PROBLEM_ID              INTEGER                         NOT NULL,
    -- Same "no unique position" reasoning as STUDY_PLAN_SECTION.POSITION above.
    POSITION                INTEGER                         NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_STUDY_PLAN_PROBLEM PRIMARY KEY (STUDY_PLAN_PROBLEM_ID),
    CONSTRAINT FK_STUDY_PLAN_PROBLEM_SECTION FOREIGN KEY (STUDY_PLAN_SECTION_ID)
        REFERENCES dev_practice.STUDY_PLAN_SECTION (STUDY_PLAN_SECTION_ID) ON DELETE CASCADE,
    -- CASCADE from PROBLEM too: a plan entry is meaningless once its problem is gone, and refusing
    -- to delete a problem because a curated list mentions it would put plans in charge of the
    -- catalog. (Deleting a problem is already rare — it is refused once anyone has submitted.)
    CONSTRAINT FK_STUDY_PLAN_PROBLEM_PROBLEM FOREIGN KEY (PROBLEM_ID)
        REFERENCES dev_practice.PROBLEM (PROBLEM_ID) ON DELETE CASCADE
);

ALTER SEQUENCE dev_practice.STUDY_PLAN_PROBLEM_SEQ
    OWNED BY dev_practice.STUDY_PLAN_PROBLEM.STUDY_PLAN_PROBLEM_ID;

-- Loading a section's problems filters on STUDY_PLAN_SECTION_ID; the PROBLEM_ID index serves the
-- ON DELETE CASCADE from PROBLEM (otherwise a sequential scan of this table per deleted problem).
CREATE INDEX IF NOT EXISTS IDX_STUDY_PLAN_PROBLEM_SECTION ON dev_practice.STUDY_PLAN_PROBLEM (STUDY_PLAN_SECTION_ID);
CREATE INDEX IF NOT EXISTS IDX_STUDY_PLAN_PROBLEM_PROBLEM ON dev_practice.STUDY_PLAN_PROBLEM (PROBLEM_ID);
