-- liquibase formatted sql
-- changeset ttg:202609040001__0.0.2__DKP-0051__widen_payment_status_cancelled logicalFilePath:EcommerceService
-- comment: Widen ecommerce.PAYMENT.STATUS to allow CANCELLED (Option A follow-up — actively cancelling a still-unconfirmed Stripe PaymentIntent when a shopper explicitly cancels an order)

-- =============================================================================
-- Postgres has no ALTER CHECK, so drop and recreate — same shape as DKP-0035's own
-- CKC_CUSTOMER_ORDER_STATUS widening. A shopper cancelling an order while its Stripe PaymentIntent
-- is still unconfirmed now actively cancels that PaymentIntent at the gateway
-- (payment.PaymentGatewayPort#cancelUnconfirmed) instead of leaving the order stuck
-- PAYMENT_PROCESSING forever — see orderstatus.PaymentHandoffService#applyGatewayCancellation's own
-- Javadoc. The resulting Payment row is marked CANCELLED, deliberately distinct from DECLINED:
-- nothing was actually declined by the gateway, the shopper simply chose not to pay.
-- =============================================================================

ALTER TABLE ecommerce.PAYMENT DROP CONSTRAINT CKC_PAYMENT_STATUS;

ALTER TABLE ecommerce.PAYMENT ADD CONSTRAINT CKC_PAYMENT_STATUS
    CHECK (STATUS IN ('PENDING', 'SUCCEEDED', 'DECLINED', 'REFUNDED', 'CANCELLED'));
