-- liquibase formatted sql
-- changeset ttg:202609020012__0.0.2__DKP-0050__add_stripe_webhook_event_table_and_payment_gateway_reference_index logicalFilePath:EcommerceService
-- comment: Add STRIPE_WEBHOOK_EVENT (US-4.5's at-least-once-delivery dedup ledger) and a partial unique index on PAYMENT.GATEWAY_REFERENCE (the webhook's own correlation key back to a Payment row)

-- =============================================================================
-- STRIPE_WEBHOOK_EVENT
-- One row per already-processed Stripe event id. STRIPE_EVENT_ID's own UNIQUE constraint, checked
-- (existsByStripeEventId) then inserted inside the same local transaction as the Payment/Order
-- update it guards, is what makes a redelivery of the same event id a safe no-op.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ecommerce.STRIPE_WEBHOOK_EVENT_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS ecommerce.STRIPE_WEBHOOK_EVENT (
    STRIPE_WEBHOOK_EVENT_ID   INTEGER                        NOT NULL,
    STRIPE_EVENT_ID           VARCHAR(255)                   NOT NULL,
    EVENT_TYPE                VARCHAR(100)                   NOT NULL,
    USR_CREATION              VARCHAR(128)                   NOT NULL,
    DTE_CREATION              TIMESTAMP WITH TIME ZONE       NOT NULL,
    USR_LAST_MODIFICATION     VARCHAR(128)                   NOT NULL,
    DTE_LAST_MODIFICATION     TIMESTAMP WITH TIME ZONE       NOT NULL,
    VERSION                   INTEGER                        NOT NULL,

    CONSTRAINT PK_STRIPE_WEBHOOK_EVENT PRIMARY KEY (STRIPE_WEBHOOK_EVENT_ID),
    CONSTRAINT UX_STRIPE_WEBHOOK_EVENT_STRIPE_EVENT_ID UNIQUE (STRIPE_EVENT_ID)
);

ALTER SEQUENCE ecommerce.STRIPE_WEBHOOK_EVENT_SEQ OWNED BY ecommerce.STRIPE_WEBHOOK_EVENT.STRIPE_WEBHOOK_EVENT_ID;

-- =============================================================================
-- Correlates an inbound webhook (which only ever names a PaymentIntent id) back to exactly one
-- Payment row. Partial (WHERE ... IS NOT NULL) since GATEWAY_REFERENCE stays nullable for the
-- brief PENDING window before any gateway response exists (Phase 1/2/3) — most rows do reach a
-- populated state, but the column itself was never meant to be NOT NULL.
-- =============================================================================

CREATE UNIQUE INDEX IF NOT EXISTS UX_PAYMENT_GATEWAY_REFERENCE ON ecommerce.PAYMENT (GATEWAY_REFERENCE)
    WHERE GATEWAY_REFERENCE IS NOT NULL;
