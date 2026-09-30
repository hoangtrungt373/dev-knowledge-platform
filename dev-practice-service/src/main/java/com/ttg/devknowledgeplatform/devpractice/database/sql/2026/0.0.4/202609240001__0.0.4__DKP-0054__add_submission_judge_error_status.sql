-- liquibase formatted sql
-- changeset ttg:202609240001__0.0.4__DKP-0054__add_submission_judge_error_status logicalFilePath:DevPracticeService
-- comment: Allow SUBMISSION.STATUS = 'JUDGE_ERROR' (judge backend failed; the submission could not be judged)
--
-- Postgres can't alter a CHECK constraint in place, so it's dropped and re-created with the widened
-- value list. Both statements run inside this changeset's single transaction (Postgres DDL is
-- transactional), so there's no window where the column is unconstrained. Re-adding the constraint
-- validates every existing row under an ACCESS EXCLUSIVE lock; fine for this table's size — a very
-- large table would instead use ADD ... NOT VALID followed by a separate VALIDATE CONSTRAINT, which
-- only needs a SHARE UPDATE EXCLUSIVE lock while scanning. Every existing value remains legal.

ALTER TABLE dev_practice.SUBMISSION DROP CONSTRAINT IF EXISTS CKC_SUBMISSION_STATUS;

ALTER TABLE dev_practice.SUBMISSION
    ADD CONSTRAINT CKC_SUBMISSION_STATUS CHECK (STATUS IN
        ('PENDING','RUNNING','ACCEPTED','WRONG_ANSWER','COMPILE_ERROR','RUNTIME_ERROR','TIME_LIMIT_EXCEEDED',
         'JUDGE_ERROR'));
