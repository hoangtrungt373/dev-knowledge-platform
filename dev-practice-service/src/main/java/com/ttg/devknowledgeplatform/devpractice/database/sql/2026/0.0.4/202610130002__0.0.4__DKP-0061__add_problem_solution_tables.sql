-- liquibase formatted sql
-- changeset ttg:202610130002__0.0.4__DKP-0061__add_problem_solution_tables logicalFilePath:DevPracticeService
-- comment: Editorial-style solutions — PROBLEM_SOLUTION (one row per approach: title, Markdown explanation, position) and PROBLEM_SOLUTION_CODE (an approach's implementations, each a link to a judge-verified REFERENCE submission, at most one per language — enforced by ProblemSolutionService, since the language lives on SUBMISSION). Seeded by service.seed.ProblemSeeder from each problem file's `solutions:` block.

-- =============================================================================
-- PROBLEM_SOLUTION
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS dev_practice.PROBLEM_SOLUTION_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS dev_practice.PROBLEM_SOLUTION (
    PROBLEM_SOLUTION_ID     INTEGER                         NOT NULL,
    PROBLEM_ID              INTEGER                         NOT NULL,
    TITLE                   VARCHAR(150)                    NOT NULL,
    -- Markdown, TEXT: an explanation with code samples and complexity analysis has no useful cap.
    EXPLANATION             TEXT                            NOT NULL,
    -- Display order, ascending. Not unique, for the same flush-order reason as STUDY_PLAN_SECTION.POSITION
    -- (DKP-0060): a future replace-all reorder would otherwise collide with the rows it replaces.
    POSITION                INTEGER                         NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_PROBLEM_SOLUTION PRIMARY KEY (PROBLEM_SOLUTION_ID),
    -- CASCADE: a solution is authored content of its problem, like its test cases. (Problem delete is
    -- already refused while USER submissions exist, so this only ever fires for unused problems.)
    CONSTRAINT FK_PROBLEM_SOLUTION_PROBLEM FOREIGN KEY (PROBLEM_ID)
        REFERENCES dev_practice.PROBLEM (PROBLEM_ID) ON DELETE CASCADE
);

ALTER SEQUENCE dev_practice.PROBLEM_SOLUTION_SEQ OWNED BY dev_practice.PROBLEM_SOLUTION.PROBLEM_SOLUTION_ID;

-- The only read path: a problem's solutions, in order.
CREATE INDEX IF NOT EXISTS IDX_PROBLEM_SOLUTION_PROBLEM ON dev_practice.PROBLEM_SOLUTION (PROBLEM_ID);

-- =============================================================================
-- PROBLEM_SOLUTION_CODE
-- An explicit join entity (approach ↔ REFERENCE submission) rather than a bare join table, for the
-- audit columns — same reasoning as PROBLEM_TAG_ASSIGNMENT. Linking the submission, instead of copying
-- its code, is the point: what a learner reads is exactly what the judge accepted.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS dev_practice.PROBLEM_SOLUTION_CODE_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS dev_practice.PROBLEM_SOLUTION_CODE (
    PROBLEM_SOLUTION_CODE_ID    INTEGER                         NOT NULL,
    PROBLEM_SOLUTION_ID         INTEGER                         NOT NULL,
    SUBMISSION_ID               INTEGER                         NOT NULL,
    USR_CREATION                VARCHAR(128)                    NOT NULL,
    DTE_CREATION                TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION       VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION       TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                     INTEGER                         NOT NULL,

    CONSTRAINT PK_PROBLEM_SOLUTION_CODE PRIMARY KEY (PROBLEM_SOLUTION_CODE_ID),
    CONSTRAINT FK_PROBLEM_SOLUTION_CODE_SOLUTION FOREIGN KEY (PROBLEM_SOLUTION_ID)
        REFERENCES dev_practice.PROBLEM_SOLUTION (PROBLEM_SOLUTION_ID) ON DELETE CASCADE,
    -- CASCADE from SUBMISSION too: ProblemServiceImpl#delete removes a problem's REFERENCE runs
    -- explicitly before the problem (FK_SUBMISSION_PROBLEM doesn't cascade), and a link to a deleted
    -- run must go with it rather than block that delete.
    CONSTRAINT FK_PROBLEM_SOLUTION_CODE_SUBMISSION FOREIGN KEY (SUBMISSION_ID)
        REFERENCES dev_practice.SUBMISSION (SUBMISSION_ID) ON DELETE CASCADE,
    CONSTRAINT UK_PROBLEM_SOLUTION_CODE_PAIR UNIQUE (PROBLEM_SOLUTION_ID, SUBMISSION_ID)
);

ALTER SEQUENCE dev_practice.PROBLEM_SOLUTION_CODE_SEQ
    OWNED BY dev_practice.PROBLEM_SOLUTION_CODE.PROBLEM_SOLUTION_CODE_ID;

-- The pair constraint's index serves lookups by solution (its leading column); the submission side
-- needs its own for the ON DELETE CASCADE from SUBMISSION.
CREATE INDEX IF NOT EXISTS IDX_PROBLEM_SOLUTION_CODE_SUBMISSION ON dev_practice.PROBLEM_SOLUTION_CODE (SUBMISSION_ID);
