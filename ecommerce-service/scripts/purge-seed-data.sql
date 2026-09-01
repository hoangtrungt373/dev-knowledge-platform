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

-- RESTART IDENTITY above is a no-op for every id column here — none of them are backed by a
-- Postgres IDENTITY/serial column "owned" by a sequence. This reactor uses explicit, standalone
-- ecommerce.*_SEQ sequences (root CLAUDE.md's "Sequences" convention) that Hibernate calls
-- nextval() on itself, with no ALTER SEQUENCE ... OWNED BY link back to any column — so
-- TRUNCATE's own identity-reset never reaches them. Reset each one by hand instead, so freshly
-- seeded rows start clean at id=1 again rather than continuing from wherever they left off.
ALTER SEQUENCE ecommerce.PRODUCT_CATEGORY_SEQ RESTART WITH 1;
ALTER SEQUENCE ecommerce.PRODUCT_SEQ RESTART WITH 1;
ALTER SEQUENCE ecommerce.PRODUCT_IMAGE_SEQ RESTART WITH 1;
ALTER SEQUENCE ecommerce.PRODUCT_VARIANT_SEQ RESTART WITH 1;
ALTER SEQUENCE ecommerce.PRODUCT_TAG_SEQ RESTART WITH 1;
ALTER SEQUENCE ecommerce.PRODUCT_TAG_ASSIGNMENT_SEQ RESTART WITH 1;
ALTER SEQUENCE ecommerce.PRODUCT_SEARCH_VIEW_SEQ RESTART WITH 1;
ALTER SEQUENCE ecommerce.OUTBOX_EVENT_SEQ RESTART WITH 1;
ALTER SEQUENCE ecommerce.CUSTOMER_ORDER_SEQ RESTART WITH 1;
ALTER SEQUENCE ecommerce.ORDER_LINE_SEQ RESTART WITH 1;
ALTER SEQUENCE ecommerce.ORDER_STATUS_HISTORY_SEQ RESTART WITH 1;

COMMIT;
