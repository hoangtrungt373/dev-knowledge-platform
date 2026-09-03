-- liquibase formatted sql
-- changeset ttg:202609020010__0.0.2__DKP-0048__add_payment_table logicalFilePath:EcommerceService
-- comment: Add ecommerce-service PAYMENT table (Epic 4: Payments, Phase 1 — US-4.2's crash-safe payment-attempt row)

-- =============================================================================
-- PAYMENT
-- Written with STATUS='PENDING' before payment.PaymentGatewayPort#charge is ever called (US-4.2),
-- in the same transaction as orderstatus.PaymentHandoffService#startPaymentProcessing's own
-- PENDING -> PAYMENT_PROCESSING order transition — this is what Epic 3's own reconciliation job
-- (US-3.4) queries against if the app crashes mid-call. GATEWAY_REFERENCE/FAILURE_CATEGORY/
-- GATEWAY_FAILURE_MESSAGE are nullable and unpopulated until later Epic 4 phases add a real
-- gateway adapter (Phase 2) and decline-reason mapping (Phase 7, US-4.7) — the full row shape is
-- added now in one pass, the same way every one of Epic 3's own Order columns was, since Epic 4's
-- own user stories already specify it end to end.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ecommerce.PAYMENT_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS ecommerce.PAYMENT (
    PAYMENT_ID                INTEGER                        NOT NULL,
    CUSTOMER_ORDER_ID          INTEGER                       NOT NULL,
    AMOUNT                     NUMERIC(12,2)                 NOT NULL,
    STATUS                     VARCHAR(20)                   NOT NULL DEFAULT 'PENDING',
    IDEMPOTENCY_KEY            VARCHAR(64)                   NOT NULL,
    GATEWAY_REFERENCE          VARCHAR(255),
    FAILURE_CATEGORY           VARCHAR(30),
    GATEWAY_FAILURE_MESSAGE    TEXT,
    USR_CREATION               VARCHAR(128)                  NOT NULL,
    DTE_CREATION                TIMESTAMP WITH TIME ZONE      NOT NULL,
    USR_LAST_MODIFICATION      VARCHAR(128)                  NOT NULL,
    DTE_LAST_MODIFICATION      TIMESTAMP WITH TIME ZONE      NOT NULL,
    VERSION                    INTEGER                       NOT NULL,

    CONSTRAINT PK_PAYMENT PRIMARY KEY (PAYMENT_ID),
    CONSTRAINT FK_PAYMENT_CUSTOMER_ORDER FOREIGN KEY (CUSTOMER_ORDER_ID)
        REFERENCES ecommerce.CUSTOMER_ORDER (CUSTOMER_ORDER_ID),
    CONSTRAINT UX_PAYMENT_IDEMPOTENCY_KEY UNIQUE (IDEMPOTENCY_KEY),
    CONSTRAINT CKC_PAYMENT_STATUS CHECK (STATUS IN ('PENDING', 'SUCCEEDED', 'DECLINED', 'REFUNDED')),
    CONSTRAINT CKC_PAYMENT_FAILURE_CATEGORY CHECK (
        FAILURE_CATEGORY IS NULL OR FAILURE_CATEGORY IN ('INSUFFICIENT_FUNDS', 'CARD_DECLINED', 'GATEWAY_ERROR')
    ),
    CONSTRAINT CKC_PAYMENT_AMOUNT_POSITIVE CHECK (AMOUNT > 0)
);

ALTER SEQUENCE ecommerce.PAYMENT_SEQ OWNED BY ecommerce.PAYMENT.PAYMENT_ID;

-- Backs PaymentRepository.findByOrderId — a payment is always looked up by its order first.
CREATE INDEX IF NOT EXISTS IDX_PAYMENT_CUSTOMER_ORDER ON ecommerce.PAYMENT (CUSTOMER_ORDER_ID);
