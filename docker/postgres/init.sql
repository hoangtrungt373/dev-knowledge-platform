-- Extensions required by the application
-- uuid-ossp: UUID generation
-- vector: pgvector for embedding storage (RAG)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS vector;

-- Keycloak's own internal Liquibase migration assumes its schema already exists and fails
-- otherwise (it does not create its own schema, unlike every other component in this project's
-- "one Postgres instance, one schema per component" convention). This used to be bootstrapped by
-- gateway's own DKP-0024 Liquibase changeset, back when gateway still had a Liquibase story at
-- all — moved here once that whole changelog tree was retired (see docs/CHANGELOG.md), since this
-- script already runs automatically before Postgres reports healthy, which every service in this
-- project's compose files already depends on. Runs on first container init only (an empty
-- docker-entrypoint-initdb.d run against a fresh volume) — same practical one-time-only
-- characteristic the old changeset had, since a schema persists in the volume once created.
CREATE SCHEMA IF NOT EXISTS keycloak;