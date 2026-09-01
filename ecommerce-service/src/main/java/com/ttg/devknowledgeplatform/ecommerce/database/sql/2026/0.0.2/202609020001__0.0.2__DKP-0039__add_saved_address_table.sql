-- liquibase formatted sql
-- changeset ttg:202609020001__0.0.2__DKP-0039__add_saved_address_table logicalFilePath:EcommerceService
-- comment: Add ecommerce.SAVED_ADDRESS — a shopper's reusable address book, reversing Epic 2's original "single inline address, no saved address book" scope lock (see Address.java's own Javadoc) now that a real need exists

-- =============================================================================
-- SAVED_ADDRESS
-- A full first-class entity, unlike Address (still a plain @Embeddable snapshotted onto
-- CUSTOMER_ORDER — no lifecycle of its own) — a saved address genuinely has an independent
-- lifecycle (create/edit/delete/set-default) that CUSTOMER_ORDER.shippingAddress never needed.
-- OWNER_UUID is a plain column, not a User foreign key — the same "claims-only, no persisted User
-- row" shape CUSTOMER_ORDER's own OWNER_UUID already established for this module (see root
-- CLAUDE.md's Security section): the only thing this table ever needs to answer is "is this row's
-- owner the caller," via the JWT's own sub claim, never another user's profile data.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ecommerce.SAVED_ADDRESS_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS ecommerce.SAVED_ADDRESS (
    SAVED_ADDRESS_ID         INTEGER                         NOT NULL,
    OWNER_UUID               VARCHAR(36)                     NOT NULL,
    LABEL                    VARCHAR(50),
    FULL_NAME                VARCHAR(150)                    NOT NULL,
    LINE_1                   VARCHAR(255)                    NOT NULL,
    LINE_2                   VARCHAR(255),
    CITY                     VARCHAR(100)                    NOT NULL,
    STATE                    VARCHAR(100)                    NOT NULL,
    POSTAL_CODE              VARCHAR(20)                     NOT NULL,
    COUNTRY                  VARCHAR(100)                    NOT NULL,
    IS_DEFAULT               BOOLEAN                         NOT NULL DEFAULT FALSE,
    USR_CREATION             VARCHAR(128)                    NOT NULL,
    DTE_CREATION             TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION    VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION    TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                  INTEGER                         NOT NULL,

    CONSTRAINT PK_SAVED_ADDRESS PRIMARY KEY (SAVED_ADDRESS_ID)
);

ALTER SEQUENCE ecommerce.SAVED_ADDRESS_SEQ OWNED BY ecommerce.SAVED_ADDRESS.SAVED_ADDRESS_ID;

-- "List my addresses" (GET /api/v1/addresses) is a genuine, real query this feature needs from
-- day one — unlike CUSTOMER_ORDER's own OWNER_UUID, which had no such query when that table was
-- first created (see that migration's own comment) and only grew one later. A plain btree index.
CREATE INDEX IX_SAVED_ADDRESS_OWNER_UUID ON ecommerce.SAVED_ADDRESS (OWNER_UUID);

-- Enforces "at most one default address per owner" at the database level, alongside
-- SavedAddressServiceImpl's own app-level "unset the previous default when a new one is set"
-- logic (belt and suspenders, same reasoning PRODUCT_TAG_ASSIGNMENT's own unique constraint
-- documents). A *partial* unique index — only indexes rows where IS_DEFAULT is TRUE — rather than
-- a plain UNIQUE(OWNER_UUID) constraint, since an owner is allowed any number of *non*-default
-- addresses; the uniqueness rule only applies to the single "this one is the default" row.
CREATE UNIQUE INDEX UX_SAVED_ADDRESS_OWNER_DEFAULT
    ON ecommerce.SAVED_ADDRESS (OWNER_UUID)
    WHERE IS_DEFAULT = TRUE;
