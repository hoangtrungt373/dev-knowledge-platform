-- Purges every ecommerce-service table so the CSV seeders (ProductCategorySeeder,
-- ProductTagSeeder, ProductSeeder, ProductImageSeeder) can be re-run from a clean slate.
--
-- Why this is needed at all: every seeder's idempotency check is a natural-key existence check
-- (e.g. ProductSeeder skips a row whose "name" already exists) — see ecommerce-service/CLAUDE.md.
-- As long as any old row survives, re-running the app with app.seed.enabled=true just silently
-- skips it instead of reseeding. This script empties every table this module owns so the next
-- startup reseeds the full sample catalog (including the fresh Product Tags data) from scratch.
--
-- Usage (from the host, against the docker-compose.infra.yml Postgres container):
--   docker exec -i dev-premier-postgres psql -U postgres -d dev-premier -f - < ecommerce-service/scripts/purge-seed-data.sql
-- or, connected via any Postgres client (psql, DBeaver, etc.) to the shared dev-premier database:
--   \i ecommerce-service/scripts/purge-seed-data.sql
--
-- Scope: the `ecommerce` schema only — this module's own tables, nothing from any sibling
-- service's schema. Cart state lives in Redis (CartServiceImpl's HINCRBY-based hash), not
-- Postgres, so it survives this script untouched; flush it separately if you also want a clean
-- cart, e.g.: docker exec -it dev-premier-redis redis-cli FLUSHDB

BEGIN;

-- Every table in the ecommerce schema, listed together so Postgres can resolve FK dependency
-- order itself in one statement — CASCADE is included as a safety net for any future table this
-- list falls behind on, not because it's required today (every FK-linked table is already listed).
-- RESTART IDENTITY is sufficient on its own here (no separate ALTER SEQUENCE ... RESTART WITH 1
-- needed) — every ecommerce.*_SEQ sequence is linked via ALTER SEQUENCE ... OWNED BY back to its
-- column (see each table's own migration), which is exactly the association Postgres's
-- TRUNCATE ... RESTART IDENTITY looks for to decide which sequences to reset.
TRUNCATE TABLE
    ecommerce.OUTBOX_EVENT,
    ecommerce.PRODUCT_SEARCH_VIEW,
    ecommerce.ORDER_STATUS_HISTORY,
    ecommerce.ORDER_LINE,
    ecommerce.CUSTOMER_ORDER,
    ecommerce.PRODUCT_TAG_ASSIGNMENT,
    ecommerce.PRODUCT_TAG,
    ecommerce.PRODUCT_IMAGE,
    ecommerce.PRODUCT_VARIANT,
    ecommerce.PRODUCT,
    ecommerce.PRODUCT_CATEGORY
    RESTART IDENTITY CASCADE;

COMMIT;
