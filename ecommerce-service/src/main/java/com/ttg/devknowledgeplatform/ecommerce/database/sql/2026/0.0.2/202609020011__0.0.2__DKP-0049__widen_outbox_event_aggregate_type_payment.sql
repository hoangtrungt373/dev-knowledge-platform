-- liquibase formatted sql
-- changeset ttg:202609020011__0.0.2__DKP-0049__widen_outbox_event_aggregate_type_payment logicalFilePath:EcommerceService
-- comment: Widen OUTBOX_EVENT's AGGREGATE_TYPE check constraint to include PAYMENT (Epic 4 Phase 4, US-4.4)

-- =============================================================================
-- Postgres has no ALTER CHECK, so drop and recreate — same shape as DKP-0035's own
-- CKC_CUSTOMER_ORDER_STATUS widening. PAYMENT is this reactor's second OutboxEvent aggregate root,
-- after PRODUCT (DKP-0023) — see OutboxAggregateType's own Javadoc for why this enum grows slowly,
-- roughly once per epic that introduces a new aggregate root, unlike EVENT_TYPE.
-- =============================================================================

ALTER TABLE ecommerce.OUTBOX_EVENT DROP CONSTRAINT CKC_OUTBOX_EVENT_AGGREGATE_TYPE;

ALTER TABLE ecommerce.OUTBOX_EVENT ADD CONSTRAINT CKC_OUTBOX_EVENT_AGGREGATE_TYPE
    CHECK (AGGREGATE_TYPE IN ('PRODUCT', 'PAYMENT'));
