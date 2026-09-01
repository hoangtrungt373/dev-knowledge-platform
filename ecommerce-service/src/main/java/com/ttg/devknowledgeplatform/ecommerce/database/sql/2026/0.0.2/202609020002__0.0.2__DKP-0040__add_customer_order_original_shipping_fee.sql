-- liquibase formatted sql
-- changeset ttg:202609020002__0.0.2__DKP-0040__add_customer_order_original_shipping_fee logicalFilePath:EcommerceService
-- comment: Adds CUSTOMER_ORDER.ORIGINAL_SHIPPING_FEE — what the shipping fee would have been absent any promotional waiver, so a placed order's own detail view can show "was $5.00, now free" the same way the checkout preview already does (see shipping.ShippingFeeQuote's own Javadoc)

-- Nullable at first (existing rows have no waiver history to backfill from beyond their own
-- already-charged SHIPPING_FEE), backfilled from SHIPPING_FEE (the only sane default — every
-- order placed before this column existed was priced by a strategy this migration has no way to
-- re-run), then tightened to NOT NULL — same three-step "add, backfill, constrain" shape as any
-- other new required column added to an already-populated table.
ALTER TABLE ecommerce.CUSTOMER_ORDER
    ADD COLUMN IF NOT EXISTS ORIGINAL_SHIPPING_FEE NUMERIC(12, 2);

UPDATE ecommerce.CUSTOMER_ORDER
    SET ORIGINAL_SHIPPING_FEE = SHIPPING_FEE
    WHERE ORIGINAL_SHIPPING_FEE IS NULL;

ALTER TABLE ecommerce.CUSTOMER_ORDER
    ALTER COLUMN ORIGINAL_SHIPPING_FEE SET NOT NULL;
