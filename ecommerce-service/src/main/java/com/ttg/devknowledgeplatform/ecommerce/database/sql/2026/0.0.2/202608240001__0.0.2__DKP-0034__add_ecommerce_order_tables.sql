-- liquibase formatted sql
-- changeset ttg:202608240001__0.0.2__DKP-0034__add_ecommerce_order_tables logicalFilePath:EcommerceService
-- comment: Add ecommerce-service order tables (Epic 2: Cart & Checkout) — CUSTOMER_ORDER (with an embedded shipping address) and ORDER_LINE, the checkout-time snapshot of what a shopper bought

-- =============================================================================
-- CUSTOMER_ORDER
-- Named CUSTOMER_ORDER, not ORDER — ORDER is a reserved keyword in PostgreSQL,
-- the same reason social-service's Group entity maps to MESSAGE_GROUP instead
-- of GROUP. SHIPPING_ADDRESS_* columns are the embedded Address value object
-- (US-2.5) — no separate ADDRESS table, since this epic locked "single inline
-- address, no saved address book" and an address has no lifecycle of its own
-- outside the order that captured it.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ecommerce.CUSTOMER_ORDER_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS ecommerce.CUSTOMER_ORDER (
    CUSTOMER_ORDER_ID       INTEGER                         NOT NULL,
    OWNER_UUID              VARCHAR(36)                     NOT NULL,
    STATUS                  VARCHAR(20)                     NOT NULL DEFAULT 'PENDING',
    FULL_NAME                VARCHAR(150)                   NOT NULL,
    LINE_1                   VARCHAR(255)                   NOT NULL,
    LINE_2                   VARCHAR(255),
    CITY                     VARCHAR(100)                   NOT NULL,
    STATE                    VARCHAR(100)                   NOT NULL,
    POSTAL_CODE              VARCHAR(20)                    NOT NULL,
    COUNTRY                  VARCHAR(100)                   NOT NULL,
    SUBTOTAL                 NUMERIC(12,2)                  NOT NULL,
    SHIPPING_FEE             NUMERIC(12,2)                  NOT NULL,
    TOTAL                    NUMERIC(12,2)                  NOT NULL,
    USR_CREATION             VARCHAR(128)                   NOT NULL,
    DTE_CREATION             TIMESTAMP WITH TIME ZONE       NOT NULL,
    USR_LAST_MODIFICATION    VARCHAR(128)                   NOT NULL,
    DTE_LAST_MODIFICATION    TIMESTAMP WITH TIME ZONE       NOT NULL,
    VERSION                  INTEGER                        NOT NULL,

    CONSTRAINT PK_CUSTOMER_ORDER PRIMARY KEY (CUSTOMER_ORDER_ID),
    -- Only PENDING exists today (Epic 2's own scope ends at order creation) — widen this list in a
    -- new migration whenever Epic 3/4 introduces a real status transition; see OrderStatus's Javadoc.
    CONSTRAINT CKC_CUSTOMER_ORDER_STATUS CHECK (STATUS IN ('PENDING'))
);

ALTER SEQUENCE ecommerce.CUSTOMER_ORDER_SEQ OWNED BY ecommerce.CUSTOMER_ORDER.CUSTOMER_ORDER_ID;

-- No index on OWNER_UUID yet — there is no "list my orders" query in this epic's scope (see
-- OrderRepository's Javadoc); add one alongside that query when it's actually built.

-- =============================================================================
-- ORDER_LINE
-- PRODUCT_VARIANT_ID is a plain column, deliberately not an FK — ProductServiceImpl.removeVariant
-- can hard-delete a variant outright, and an already-placed order must remain valid regardless.
-- SKU/PRODUCT_NAME/UNIT_PRICE are copied from the variant/product at checkout time for the same
-- reason (US-2.6) — this row must keep telling the truth about what was bought even if the
-- catalog changes afterward. No LINE_TOTAL column — derived at read time (UNIT_PRICE * QUANTITY),
-- same as a cart line's total, rather than persisting a value that could only ever drift.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ecommerce.ORDER_LINE_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS ecommerce.ORDER_LINE (
    ORDER_LINE_ID            INTEGER                        NOT NULL,
    ORDER_ID                 INTEGER                        NOT NULL,
    PRODUCT_VARIANT_ID        INTEGER                       NOT NULL,
    SKU                       VARCHAR(64)                   NOT NULL,
    PRODUCT_NAME              VARCHAR(150)                  NOT NULL,
    UNIT_PRICE                NUMERIC(12,2)                 NOT NULL,
    QUANTITY                  INTEGER                       NOT NULL,
    USR_CREATION              VARCHAR(128)                  NOT NULL,
    DTE_CREATION              TIMESTAMP WITH TIME ZONE      NOT NULL,
    USR_LAST_MODIFICATION     VARCHAR(128)                  NOT NULL,
    DTE_LAST_MODIFICATION     TIMESTAMP WITH TIME ZONE      NOT NULL,
    VERSION                   INTEGER                       NOT NULL,

    CONSTRAINT PK_ORDER_LINE PRIMARY KEY (ORDER_LINE_ID),
    CONSTRAINT FK_ORDER_LINE_CUSTOMER_ORDER FOREIGN KEY (ORDER_ID)
        REFERENCES ecommerce.CUSTOMER_ORDER (CUSTOMER_ORDER_ID),
    CONSTRAINT CKC_ORDER_LINE_QUANTITY_POSITIVE CHECK (QUANTITY > 0)
);

ALTER SEQUENCE ecommerce.ORDER_LINE_SEQ OWNED BY ecommerce.ORDER_LINE.ORDER_LINE_ID;

CREATE INDEX IF NOT EXISTS IDX_ORDER_LINE_CUSTOMER_ORDER ON ecommerce.ORDER_LINE (ORDER_ID);
