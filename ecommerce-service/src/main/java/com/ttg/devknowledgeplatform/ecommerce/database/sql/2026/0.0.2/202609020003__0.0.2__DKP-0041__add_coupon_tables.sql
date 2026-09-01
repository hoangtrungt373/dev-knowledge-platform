-- liquibase formatted sql
-- changeset ttg:202609020003__0.0.2__DKP-0041__add_coupon_tables logicalFilePath:EcommerceService
-- comment: Adds COUPON (code-driven discounts — target SUBTOTAL|SHIPPING_FEE, type PERCENTAGE|FIXED_AMOUNT, with active/date-range/min-subtotal/redemption-limit conditions) and COUPON_REDEMPTION (the audit ledger a coupon's own redemption limits are enforced against) — Phase 1 (data model + basic admin CRUD) of the "ProductDiscount"/Coupon feature; checkout integration (Phase 2) and product/category eligibility scoping (Phase 3) land in later migrations

-- =============================================================================
-- COUPON
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ecommerce.COUPON_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS ecommerce.COUPON (
    COUPON_ID                     INTEGER                         NOT NULL,
    CODE                          VARCHAR(50)                     NOT NULL,
    TARGET                        VARCHAR(20)                     NOT NULL,
    TYPE                          VARCHAR(20)                     NOT NULL,
    VALUE                         NUMERIC(12, 2)                  NOT NULL,
    ACTIVE                        BOOLEAN                         NOT NULL DEFAULT TRUE,
    START_AT                      TIMESTAMP WITH TIME ZONE,
    END_AT                        TIMESTAMP WITH TIME ZONE,
    MIN_SUBTOTAL                  NUMERIC(12, 2),
    MAX_REDEMPTIONS               INTEGER,
    MAX_REDEMPTIONS_PER_USER      INTEGER,
    USR_CREATION                  VARCHAR(128)                    NOT NULL,
    DTE_CREATION                  TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION         VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION         TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                       INTEGER                         NOT NULL,

    CONSTRAINT PK_COUPON PRIMARY KEY (COUPON_ID),
    CONSTRAINT UK_COUPON_CODE UNIQUE (CODE),
    CONSTRAINT CKC_COUPON_TARGET CHECK (TARGET IN ('SUBTOTAL', 'SHIPPING_FEE')),
    CONSTRAINT CKC_COUPON_TYPE CHECK (TYPE IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT CKC_COUPON_VALUE_POSITIVE CHECK (VALUE > 0)
);

ALTER SEQUENCE ecommerce.COUPON_SEQ OWNED BY ecommerce.COUPON.COUPON_ID;

-- Codes are normalized to uppercase before persisting (CouponServiceImpl.normalizeCode), so a
-- plain UNIQUE constraint on CODE is correctly case-insensitive in practice — unlike
-- PRODUCT_TAG.NAME, which preserves the admin's own casing and so needs a functional
-- LOWER(NAME) index instead.

-- =============================================================================
-- COUPON_REDEMPTION
-- The ledger COUPON.MAX_REDEMPTIONS/MAX_REDEMPTIONS_PER_USER are enforced against (Phase 2), and
-- the audit trail for "which coupon, how much" on a given order. Real FKs to both COUPON and
-- CUSTOMER_ORDER — unlike ORDER_LINE.PRODUCT_VARIANT_ID's deliberately-plain-column shape, neither
-- parent here can ever be hard-deleted out from under a redemption row: a coupon still in use is
-- rejected at delete time (CouponServiceImpl.delete, COUPON_IN_USE), and CUSTOMER_ORDER rows are
-- permanent records with no delete path anywhere in this module.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ecommerce.COUPON_REDEMPTION_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS ecommerce.COUPON_REDEMPTION (
    COUPON_REDEMPTION_ID     INTEGER                         NOT NULL,
    COUPON_ID                INTEGER                         NOT NULL,
    CUSTOMER_ORDER_ID        INTEGER                         NOT NULL,
    OWNER_UUID               VARCHAR(36)                     NOT NULL,
    DISCOUNT_AMOUNT          NUMERIC(12, 2)                  NOT NULL,
    USR_CREATION             VARCHAR(128)                    NOT NULL,
    DTE_CREATION             TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION    VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION    TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                  INTEGER                         NOT NULL,

    CONSTRAINT PK_COUPON_REDEMPTION PRIMARY KEY (COUPON_REDEMPTION_ID),
    CONSTRAINT FK_COUPON_REDEMPTION_COUPON FOREIGN KEY (COUPON_ID)
        REFERENCES ecommerce.COUPON (COUPON_ID),
    CONSTRAINT FK_COUPON_REDEMPTION_ORDER FOREIGN KEY (CUSTOMER_ORDER_ID)
        REFERENCES ecommerce.CUSTOMER_ORDER (CUSTOMER_ORDER_ID)
);

ALTER SEQUENCE ecommerce.COUPON_REDEMPTION_SEQ
    OWNED BY ecommerce.COUPON_REDEMPTION.COUPON_REDEMPTION_ID;

-- Backs both MAX_REDEMPTIONS (COUNT(*) WHERE COUPON_ID = :id) and MAX_REDEMPTIONS_PER_USER
-- (COUNT(*) WHERE COUPON_ID = :id AND OWNER_UUID = :ownerUuid) enforcement, once Phase 2 wires it up.
CREATE INDEX IF NOT EXISTS IDX_COUPON_REDEMPTION_COUPON ON ecommerce.COUPON_REDEMPTION (COUPON_ID);
CREATE INDEX IF NOT EXISTS IDX_COUPON_REDEMPTION_COUPON_OWNER ON ecommerce.COUPON_REDEMPTION (COUPON_ID, OWNER_UUID);
CREATE INDEX IF NOT EXISTS IDX_COUPON_REDEMPTION_ORDER ON ecommerce.COUPON_REDEMPTION (CUSTOMER_ORDER_ID);
