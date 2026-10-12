-- liquibase formatted sql
-- changeset ttg:202610120001__0.0.4__DKP-0059__add_submission_in_progress_index logicalFilePath:DevPracticeService
-- comment: Partial index for StaleSubmissionSweeper — finds PENDING/RUNNING submissions not updated for a while.

-- The sweeper runs every minute with: WHERE STATUS IN ('PENDING','RUNNING') AND DTE_LAST_MODIFICATION < ?
-- SUBMISSION grows with every attempt ever made, but only the handful currently being judged match
-- that STATUS filter. A PARTIAL index (the WHERE clause below) contains exactly those rows — a row
-- enters it when created PENDING and leaves it the moment it gets a final status — so it stays tiny
-- however large the table grows, and costs almost nothing to maintain. A plain index on
-- (STATUS, DTE_LAST_MODIFICATION) would work too, but would carry an entry for every finished
-- submission forever just to serve this one query. B-tree on DTE_LAST_MODIFICATION, since the query
-- is a range ("older than the cutoff"). The query must repeat this exact STATUS condition for
-- Postgres to use the index — SubmissionRepository#findStaleInProgressIds passes exactly
-- (PENDING, RUNNING).
CREATE INDEX IF NOT EXISTS IDX_SUBMISSION_IN_PROGRESS
    ON dev_practice.SUBMISSION (DTE_LAST_MODIFICATION)
    WHERE STATUS IN ('PENDING', 'RUNNING');
