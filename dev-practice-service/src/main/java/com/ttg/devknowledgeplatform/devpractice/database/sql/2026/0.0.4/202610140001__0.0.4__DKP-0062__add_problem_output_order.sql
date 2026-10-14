-- liquibase formatted sql
-- changeset ttg:202610140001__0.0.4__DKP-0062__add_problem_output_order logicalFilePath:DevPracticeService
-- comment: PROBLEM.OUTPUT_ORDER — whether a problem's answer must match in order or may come back in any order.

-- Part of the grading contract (enums.OutputOrder): ANY_ORDER makes judge.OutputMatcher sort every
-- array level of both outputs before comparing ("Group Anagrams", "Top K Frequent Elements").
-- NOT NULL DEFAULT 'ORDERED' backfills every existing problem with the only behaviour the judge had
-- before this column — exact order — so no published problem's verdicts change. VARCHAR + a CHECK
-- constraint rather than a Postgres ENUM type, same as STATUS/KIND elsewhere in this schema: adding a
-- value later is a constraint swap, while ALTER TYPE ... ADD VALUE can't run inside a transaction on
-- older Postgres versions.
ALTER TABLE dev_practice.PROBLEM
    ADD COLUMN IF NOT EXISTS OUTPUT_ORDER VARCHAR(20) NOT NULL DEFAULT 'ORDERED';

ALTER TABLE dev_practice.PROBLEM
    ADD CONSTRAINT CKC_PROBLEM_OUTPUT_ORDER CHECK (OUTPUT_ORDER IN ('ORDERED', 'ANY_ORDER'));
