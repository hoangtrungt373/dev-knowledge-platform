-- Purges every dev-practice-service table so the startup seeders (ProblemTagSeeder, then
-- ProblemSeeder — see service.seed.DevPracticeDataSeedingRunner) can be re-run from a clean slate.
--
-- Why this is needed at all: both seeders are idempotent by natural key — ProblemTagSeeder skips a
-- tag whose name already exists (case-insensitively), ProblemSeeder skips a problem whose slug
-- already exists (see dev-practice-service/CLAUDE.md). As long as an old row survives, restarting
-- with app.seed.enabled=true silently skips it instead of reseeding — so an edited seed file (new
-- test cases, a reworded description, a renamed tag) never reaches a database that already has the
-- old version. This script empties every table this module owns so the next startup reseeds the
-- full catalog from data/csv/problem_tags.csv and data/problems/*.md.
--
-- NOTE this is a genuine "every table" purge, not just the seeded ones: it also truncates
-- SUBMISSION — every user's real submission history and judging verdicts, which no seeder ever
-- creates. That's deliberate (a problem can't be deleted while it has submissions, so a clean
-- slate has to take them too), and it bypasses the app's own PROBLEM_HAS_SUBMISSIONS guard. Only run
-- this against a local/dev database you're fine wiping entirely, never anything with real user data
-- you want to keep.
--
-- Usage (from the repo root, against the docker-compose.infra.yml Postgres container):
--   docker exec -i dev-premier-postgres psql -U postgres -d dev-premier -f - < dev-practice-service/scripts/purge-seed-data.sql
-- or, connected via any Postgres client (psql, DBeaver, etc.) to the shared dev-premier database:
--   \i dev-practice-service/scripts/purge-seed-data.sql
-- then restart dev-practice-service with the local profile (or APP_SEED_ENABLED=true) to reseed.
--
-- Scope: the `dev_practice` schema only — this module's own tables, nothing from any sibling
-- service's schema, and not Liquibase's own DATABASECHANGELOG bookkeeping (so no migration re-runs;
-- the schema itself is untouched, only its rows). Nothing outside Postgres needs clearing: Judge0
-- keeps no state on this service's behalf (a judged submission's verdict lives in SUBMISSION).

BEGIN;

-- Every table in the dev_practice schema, listed together so Postgres resolves FK order itself in
-- one statement. CASCADE is a safety net for a future table this list falls behind on, not required
-- today (every FK-linked table is listed): TEST_CASE/METHOD_PARAMETER/PROBLEM_TAG_ASSIGNMENT
-- reference PROBLEM, PROBLEM_TAG_ASSIGNMENT also references PROBLEM_TAG, SUBMISSION references
-- PROBLEM.
-- RESTART IDENTITY alone resets every *_SEQ sequence — each one is linked to its id column with
-- ALTER SEQUENCE ... OWNED BY in its own migration, which is the association TRUNCATE ... RESTART
-- IDENTITY uses to find them. Reseeded ids therefore start from 1 again.
-- Confirmed against every migration's own CREATE TABLE statement (6 tables as of DKP-0055) —
-- re-derive the list from a grep for `CREATE TABLE IF NOT EXISTS dev_practice\.` rather than
-- trusting this count when a new table lands.
TRUNCATE TABLE
    dev_practice.SUBMISSION,
    dev_practice.PROBLEM_TAG_ASSIGNMENT,
    dev_practice.TEST_CASE,
    dev_practice.METHOD_PARAMETER,
    dev_practice.PROBLEM,
    dev_practice.PROBLEM_TAG
    RESTART IDENTITY CASCADE;

COMMIT;
