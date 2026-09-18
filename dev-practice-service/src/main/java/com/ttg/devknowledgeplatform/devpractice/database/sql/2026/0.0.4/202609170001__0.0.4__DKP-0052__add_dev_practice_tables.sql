-- liquibase formatted sql
-- changeset ttg:202609170001__0.0.4__DKP-0052__add_dev_practice_tables logicalFilePath:DevPracticeService
-- comment: Add dev-practice-service's own PROBLEM/TEST_CASE/SUBMISSION tables in a new `dev_practice` schema
--
-- Fresh snapshot of the module's Phase 1 shape (problem catalog + submission persistence, no
-- judging pipeline yet) — not a replay of any other module's history, same convention every other
-- standalone service's first changelog in this reactor follows (task-service's DKP-0028,
-- content-service's DKP-0031, ai-service's DKP-0032, etc.).
--
-- PROBLEM.AUTHOR_UUID and SUBMISSION.USER_UUID are both plain columns (the Keycloak JWT's `sub`
-- claim), never a foreign key to a local USER table — this module has no local User copy at all,
-- same "Option C" shape as task-service's OWNER_UUID/content-service's AUTHOR_UUID (see root
-- CLAUDE.md's Security section for the full reasoning).
--
-- TEST_CASE cascades from PROBLEM (ON DELETE CASCADE) since a test case never outlives its
-- problem. SUBMISSION intentionally has NO cascade/ON DELETE rule on its PROBLEM_ID foreign key
-- (left as the database default, RESTRICT) — a submission is a historical record of what a user
-- actually ran, and should never silently disappear (or block on) a problem edit; deleting a
-- problem that already has submissions against it is a Phase-2 concern to design deliberately,
-- not something this migration should decide by accident via an ON DELETE clause.

CREATE SCHEMA IF NOT EXISTS dev_practice;

CREATE SEQUENCE IF NOT EXISTS dev_practice.PROBLEM_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS dev_practice.PROBLEM (
    PROBLEM_ID              INTEGER                         NOT NULL,
    TITLE                   VARCHAR(255)                    NOT NULL,
    SLUG                    VARCHAR(255)                    NOT NULL,
    DESCRIPTION             TEXT                            NOT NULL,
    DIFFICULTY              VARCHAR(50)                     NOT NULL,
    STATUS                  VARCHAR(50)                     NOT NULL,
    AUTHOR_UUID             VARCHAR(36)                     NOT NULL,
    PUBLISHED_AT            TIMESTAMP WITH TIME ZONE,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_PROBLEM PRIMARY KEY (PROBLEM_ID),
    CONSTRAINT UQ_PROBLEM_SLUG UNIQUE (SLUG),
    CONSTRAINT CKC_PROBLEM_DIFFICULTY CHECK (DIFFICULTY IN ('EASY','MEDIUM','HARD')),
    CONSTRAINT CKC_PROBLEM_STATUS CHECK (STATUS IN ('DRAFT','PUBLISHED','ARCHIVED'))
);

ALTER SEQUENCE dev_practice.PROBLEM_SEQ OWNED BY dev_practice.PROBLEM.PROBLEM_ID;

CREATE INDEX IF NOT EXISTS IDX_PROBLEM_STATUS ON dev_practice.PROBLEM (STATUS);

CREATE SEQUENCE IF NOT EXISTS dev_practice.TEST_CASE_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS dev_practice.TEST_CASE (
    TEST_CASE_ID            INTEGER                         NOT NULL,
    PROBLEM_ID              INTEGER                         NOT NULL,
    INPUT                   TEXT                            NOT NULL,
    EXPECTED_OUTPUT         TEXT                            NOT NULL,
    SAMPLE                  BOOLEAN                         NOT NULL DEFAULT FALSE,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_TEST_CASE PRIMARY KEY (TEST_CASE_ID),
    CONSTRAINT FK_TEST_CASE_PROBLEM FOREIGN KEY (PROBLEM_ID)
        REFERENCES dev_practice.PROBLEM (PROBLEM_ID) ON DELETE CASCADE
);

ALTER SEQUENCE dev_practice.TEST_CASE_SEQ OWNED BY dev_practice.TEST_CASE.TEST_CASE_ID;

CREATE INDEX IF NOT EXISTS IDX_TEST_CASE_PROBLEM ON dev_practice.TEST_CASE (PROBLEM_ID);

CREATE SEQUENCE IF NOT EXISTS dev_practice.SUBMISSION_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS dev_practice.SUBMISSION (
    SUBMISSION_ID           INTEGER                         NOT NULL,
    PROBLEM_ID              INTEGER                         NOT NULL,
    USER_UUID               VARCHAR(36)                     NOT NULL,
    LANGUAGE                VARCHAR(50)                     NOT NULL,
    SOURCE_CODE             TEXT                            NOT NULL,
    STATUS                  VARCHAR(50)                     NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_SUBMISSION PRIMARY KEY (SUBMISSION_ID),
    CONSTRAINT FK_SUBMISSION_PROBLEM FOREIGN KEY (PROBLEM_ID)
        REFERENCES dev_practice.PROBLEM (PROBLEM_ID),
    CONSTRAINT CKC_SUBMISSION_STATUS CHECK (STATUS IN
        ('PENDING','RUNNING','ACCEPTED','WRONG_ANSWER','COMPILE_ERROR','RUNTIME_ERROR','TIME_LIMIT_EXCEEDED'))
);

ALTER SEQUENCE dev_practice.SUBMISSION_SEQ OWNED BY dev_practice.SUBMISSION.SUBMISSION_ID;

CREATE INDEX IF NOT EXISTS IDX_SUBMISSION_USER ON dev_practice.SUBMISSION (USER_UUID);
CREATE INDEX IF NOT EXISTS IDX_SUBMISSION_PROBLEM ON dev_practice.SUBMISSION (PROBLEM_ID);
