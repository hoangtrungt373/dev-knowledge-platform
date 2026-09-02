-- Purges every ecommerce-service table so the CSV seeders (ProductCategorySeeder,
-- ProductTagSeeder, ProductAttributeSeeder, ProductCategoryAttributeSeeder, ProductSeeder,
-- ProductImageSeeder, CouponSeeder) can be re-run from a clean slate.
--
-- Why this is needed at all: every seeder's idempotency check is a natural-key existence check
-- (e.g. ProductSeeder skips a row whose "name" already exists) — see ecommerce-service/CLAUDE.md.
-- As long as any old row survives, re-running the app with app.seed.enabled=true just silently
-- skips it instead of reseeding. This script empties every table this module owns so the next
-- startup reseeds the full sample catalog (including the fresh Product Tags data) from scratch.
--
-- NOTE this is a genuine "every table" purge, not just the CSV-seeded ones: it also truncates
-- CUSTOMER_ORDER/ORDER_LINE/ORDER_STATUS_HISTORY/COUPON_REDEMPTION (real checkout activity) and
-- SAVED_ADDRESS (a shopper's own real AddressBook entries — no seeder ever creates one, but the
-- table is still cleared for a genuinely clean slate). Only run this against a local/dev database
-- you're fine wiping entirely, never anything with real user data you want to keep.
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
-- COUPON_REDEMPTION/COUPON are included here (a seeded coupon has no FK from anything else, but
-- COUPON_REDEMPTION FKs onto both COUPON and CUSTOMER_ORDER). SAVED_ADDRESS has no seeder of its
-- own (no CSV-driven AddressBook seed — only real shopper-entered data) but is included anyway —
-- a shopper's saved addresses are exactly the kind of leftover dev/test data a genuinely clean
-- slate should clear too, not just whatever the CSV seeders themselves populate.
-- PRODUCT_CATEGORY_ATTRIBUTE/PRODUCT_ATTRIBUTE_VALUE/PRODUCT_ATTRIBUTE (DKP-0047, the "Option B"
-- global attribute registry) are included the same way PRODUCT_TAG_ASSIGNMENT/PRODUCT_TAG are —
-- ProductAttributeSeeder's/ProductCategoryAttributeSeeder's own idempotency checks are natural-key
-- existence checks (ProductAttribute.name, ProductCategoryAttribute's per-category "already has
-- any assignment" check) exactly like every other seeder in this module, so a surviving row here
-- silently skips reseeding the same way a surviving PRODUCT_TAG row would.
-- Confirmed against every migration's own CREATE TABLE statement (17 tables total as of DKP-0047)
-- — this script's own header promises "every ecommerce-service table"; re-derive this count from
-- a reactor-wide grep for `CREATE TABLE IF NOT EXISTS ecommerce\.` rather than trusting this
-- number if a new table lands here later (see ecommerce-service/CLAUDE.md's own note on this).
TRUNCATE TABLE
    ecommerce.OUTBOX_EVENT,
    ecommerce.PRODUCT_SEARCH_VIEW,
    ecommerce.COUPON_REDEMPTION,
    ecommerce.ORDER_STATUS_HISTORY,
    ecommerce.ORDER_LINE,
    ecommerce.CUSTOMER_ORDER,
    ecommerce.SAVED_ADDRESS,
    ecommerce.COUPON,
    ecommerce.PRODUCT_TAG_ASSIGNMENT,
    ecommerce.PRODUCT_TAG,
    ecommerce.PRODUCT_CATEGORY_ATTRIBUTE,
    ecommerce.PRODUCT_ATTRIBUTE_VALUE,
    ecommerce.PRODUCT_ATTRIBUTE,
    ecommerce.PRODUCT_IMAGE,
    ecommerce.PRODUCT_VARIANT,
    ecommerce.PRODUCT,
    ecommerce.PRODUCT_CATEGORY
    RESTART IDENTITY CASCADE;

COMMIT;
