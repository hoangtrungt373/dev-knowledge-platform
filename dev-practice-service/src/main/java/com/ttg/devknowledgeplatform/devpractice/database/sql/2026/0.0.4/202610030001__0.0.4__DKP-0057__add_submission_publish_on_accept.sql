-- liquibase formatted sql
-- changeset ttg:202610030001__0.0.4__DKP-0057__add_submission_publish_on_accept logicalFilePath:DevPracticeService
-- comment: Publish-on-accept — a REFERENCE submission can ask for its DRAFT problem to be published automatically once it is judged ACCEPTED (used by ProblemSeeder; optional for the admin API).

-- A persisted flag rather than something held in memory by the caller: judging is asynchronous
-- (SubmissionJudgeEventListener runs after the submitting transaction commits, on another thread),
-- so the intent has to travel with the row to the place the verdict is known. BOOLEAN NOT NULL DEFAULT
-- FALSE backfills every existing row as "don't publish" in the same statement — the only correct
-- value for them, since none was submitted with this intent.
ALTER TABLE dev_practice.SUBMISSION
    ADD COLUMN IF NOT EXISTS PUBLISH_ON_ACCEPT BOOLEAN NOT NULL DEFAULT FALSE;
