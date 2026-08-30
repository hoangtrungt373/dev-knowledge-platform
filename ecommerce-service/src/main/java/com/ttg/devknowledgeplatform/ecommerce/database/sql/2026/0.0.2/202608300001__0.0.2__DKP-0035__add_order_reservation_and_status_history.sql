-- liquibase formatted sql
-- changeset ttg:202608300001__0.0.2__DKP-0035__add_order_reservation_and_status_history logicalFilePath:EcommerceService
-- comment: Epic 3 (Order Lifecycle & Inventory) foundation — widen CUSTOMER_ORDER.STATUS to the full state machine, add the columns US-3.3/3.6 need (idempotency key, payment-processing clock, queued-cancel flag), add the index the expiry/reconciliation jobs will poll on, and add ORDER_STATUS_HISTORY (US-3.5's audit log)

-- =============================================================================
-- CUSTOMER_ORDER — widen the status CHECK constraint to the full Epic 3 state
-- machine in one pass (see OrderStatus's own Javadoc for why this isn't grown
-- incrementally the way OutboxAggregateType's CHECK is). Postgres has no
-- ALTER CHECK, so drop and recreate.
-- =============================================================================

ALTER TABLE ecommerce.CUSTOMER_ORDER DROP CONSTRAINT CKC_CUSTOMER_ORDER_STATUS;

ALTER TABLE ecommerce.CUSTOMER_ORDER ADD CONSTRAINT CKC_CUSTOMER_ORDER_STATUS
    CHECK (STATUS IN ('PENDING', 'PAYMENT_PROCESSING', 'CONFIRMED', 'EXPIRED', 'FAILED', 'CANCELLED', 'SHIPPED', 'DELIVERED'));

-- US-3.3: stamped once, immediately before PENDING -> PAYMENT_PROCESSING; nullable, since it has no
-- meaning before that transition. Uniqueness is enforced by a partial index below rather than a
-- plain UNIQUE column constraint, since most rows (every PENDING order that never reaches payment)
-- will have NULL here and Postgres treats NULLs as distinct for UNIQUE anyway — the partial index
-- documents that reasoning explicitly instead of relying on that default behavior silently.
ALTER TABLE ecommerce.CUSTOMER_ORDER ADD COLUMN IDEMPOTENCY_KEY VARCHAR(64);

-- US-3.4: the reconciliation job's own "how long has this been stuck" clock — separate from
-- DTE_CREATION, since an order can sit PENDING for a while before payment is ever attempted.
ALTER TABLE ecommerce.CUSTOMER_ORDER ADD COLUMN PAYMENT_PROCESSING_STARTED_AT TIMESTAMP WITH TIME ZONE;

-- US-3.6: a cancel requested mid-PAYMENT_PROCESSING can't jump straight to CANCELLED (a gateway
-- call is literally in flight) — this flag queues it instead, consulted once that call resolves.
ALTER TABLE ecommerce.CUSTOMER_ORDER ADD COLUMN CANCEL_REQUESTED BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS UQ_CUSTOMER_ORDER_IDEMPOTENCY_KEY
    ON ecommerce.CUSTOMER_ORDER (IDEMPOTENCY_KEY) WHERE IDEMPOTENCY_KEY IS NOT NULL;

-- Supports both the reservation-expiry job's poll query (WHERE STATUS = 'PENDING' AND
-- DTE_CREATION < :cutoff, US-3.2) and the reconciliation job's (WHERE STATUS =
-- 'PAYMENT_PROCESSING' AND PAYMENT_PROCESSING_STARTED_AT < :cutoff, US-3.4) — both filter on
-- STATUS first, so one composite index on (STATUS, DTE_CREATION) serves the first job directly and
-- still lets Postgres use the STATUS-only leading column for the second.
CREATE INDEX IF NOT EXISTS IDX_CUSTOMER_ORDER_STATUS_DTE_CREATION
    ON ecommerce.CUSTOMER_ORDER (STATUS, DTE_CREATION);

-- =============================================================================
-- ORDER_STATUS_HISTORY
-- One row per lifecycle transition (US-3.5). FROM_STATUS is NULL only for the
-- very first row (order creation has no "from" state) — a column-nullable
-- check, not a third CHECK branch. DTE_CREATION (the audit column every table
-- already carries) doubles as this row's own "when did this happen" timestamp.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS ecommerce.ORDER_STATUS_HISTORY_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS ecommerce.ORDER_STATUS_HISTORY (
    ORDER_STATUS_HISTORY_ID  INTEGER                        NOT NULL,
    ORDER_ID                 INTEGER                        NOT NULL,
    FROM_STATUS              VARCHAR(20),
    TO_STATUS                VARCHAR(20)                    NOT NULL,
    REASON                   VARCHAR(255),
    USR_CREATION             VARCHAR(128)                   NOT NULL,
    DTE_CREATION             TIMESTAMP WITH TIME ZONE       NOT NULL,
    USR_LAST_MODIFICATION    VARCHAR(128)                   NOT NULL,
    DTE_LAST_MODIFICATION    TIMESTAMP WITH TIME ZONE       NOT NULL,
    VERSION                  INTEGER                        NOT NULL,

    CONSTRAINT PK_ORDER_STATUS_HISTORY PRIMARY KEY (ORDER_STATUS_HISTORY_ID),
    CONSTRAINT FK_ORDER_STATUS_HISTORY_CUSTOMER_ORDER FOREIGN KEY (ORDER_ID)
        REFERENCES ecommerce.CUSTOMER_ORDER (CUSTOMER_ORDER_ID),
    -- Same widened list as CKC_CUSTOMER_ORDER_STATUS above.
    CONSTRAINT CKC_ORDER_STATUS_HISTORY_FROM_STATUS CHECK (FROM_STATUS IS NULL OR FROM_STATUS IN
        ('PENDING', 'PAYMENT_PROCESSING', 'CONFIRMED', 'EXPIRED', 'FAILED', 'CANCELLED', 'SHIPPED', 'DELIVERED')),
    CONSTRAINT CKC_ORDER_STATUS_HISTORY_TO_STATUS CHECK (TO_STATUS IN
        ('PENDING', 'PAYMENT_PROCESSING', 'CONFIRMED', 'EXPIRED', 'FAILED', 'CANCELLED', 'SHIPPED', 'DELIVERED'))
);

ALTER SEQUENCE ecommerce.ORDER_STATUS_HISTORY_SEQ OWNED BY ecommerce.ORDER_STATUS_HISTORY.ORDER_STATUS_HISTORY_ID;

CREATE INDEX IF NOT EXISTS IDX_ORDER_STATUS_HISTORY_CUSTOMER_ORDER ON ecommerce.ORDER_STATUS_HISTORY (ORDER_ID);
