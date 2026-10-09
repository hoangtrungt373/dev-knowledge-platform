-- liquibase formatted sql
-- changeset ttg:202610090001__0.0.4__DKP-0058__add_submission_runtime_memory logicalFilePath:DevPracticeService
-- comment: Runtime and memory of an ACCEPTED submission — the maximum over its test cases, as measured by Judge0.

-- INTEGER, nullable, no default: NULL means "not measured" — every existing row (judged before
-- Judge0's time/memory were read), every non-ACCEPTED submission (a failing run's runtime isn't a
-- score), and anything still PENDING/RUNNING. A default of 0 would wrongly claim "measured as free".
-- Milliseconds and kilobytes are whole numbers at the precision Judge0 reports (it gives CPU seconds
-- with millisecond precision and memory in KB), so INTEGER loses nothing; the maximum fits easily
-- (2^31 ms ≈ 24 days, 2^31 KB ≈ 2 TB), and 4 bytes per column beats NUMERIC's variable width.
ALTER TABLE dev_practice.SUBMISSION
    ADD COLUMN IF NOT EXISTS RUNTIME_MS INTEGER;

ALTER TABLE dev_practice.SUBMISSION
    ADD COLUMN IF NOT EXISTS MEMORY_KB INTEGER;
