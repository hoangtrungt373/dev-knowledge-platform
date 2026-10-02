-- liquibase formatted sql
-- changeset ttg:202610020001__0.0.4__DKP-0056__add_problem_verification_columns logicalFilePath:DevPracticeService
-- comment: Problem verification — a problem can only be published once an admin's REFERENCE submission is ACCEPTED at the problem's current contract version (signature + test cases).

-- PROBLEM.CONTRACT_VERSION: bumped by ProblemServiceImpl whenever the grading contract changes (method
-- signature, or any test case's input/expected output). Distinct from the VERSION audit column, which is
-- Hibernate's optimistic-locking counter and changes on every save (a title edit included). DEFAULT 1
-- backfills existing rows in the same statement, so NOT NULL is safe on a populated table (Postgres 11+
-- stores a constant default in the catalog — no table rewrite).
ALTER TABLE dev_practice.PROBLEM
    ADD COLUMN IF NOT EXISTS CONTRACT_VERSION INTEGER NOT NULL DEFAULT 1;

-- SUBMISSION.KIND: USER (a user's attempt) or REFERENCE (an admin's verification run). Existing rows are
-- all user submissions — reference submissions didn't exist before this changeset — hence DEFAULT 'USER'.
ALTER TABLE dev_practice.SUBMISSION
    ADD COLUMN IF NOT EXISTS KIND VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE dev_practice.SUBMISSION
    ADD CONSTRAINT CKC_SUBMISSION_KIND CHECK (KIND IN ('USER', 'REFERENCE'));

-- SUBMISSION.CONTRACT_VERSION: the problem's CONTRACT_VERSION the submission was judged against, stamped
-- by SubmissionJudgeEventListener in the same transaction that loads the test cases it judges. NULL until
-- judging starts, and for every pre-existing row (their contract version at judge time is unknown, and
-- none of them is a REFERENCE, so nothing ever compares it).
ALTER TABLE dev_practice.SUBMISSION
    ADD COLUMN IF NOT EXISTS CONTRACT_VERSION INTEGER;

-- Backs the one question this feature asks on every publish: "is there an ACCEPTED REFERENCE for this
-- problem at this contract version?" A partial index — only rows where KIND = 'REFERENCE' AND
-- STATUS = 'ACCEPTED' are indexed — so it stays tiny (one or two rows per problem) no matter how many
-- user submissions accumulate, and the query becomes an index-only lookup on (PROBLEM_ID,
-- CONTRACT_VERSION). Postgres only uses a partial index when the query's WHERE clause implies the
-- index's predicate, which SubmissionRepository#existsByProblem_IdAndKindAndStatusAndContractVersion's
-- generated SQL does (it filters on both columns with those exact values).
CREATE INDEX IF NOT EXISTS IDX_SUBMISSION_ACCEPTED_REFERENCE
    ON dev_practice.SUBMISSION (PROBLEM_ID, CONTRACT_VERSION)
    WHERE KIND = 'REFERENCE' AND STATUS = 'ACCEPTED';
