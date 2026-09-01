-- liquibase formatted sql
-- changeset ttg:202609020004__0.0.2__DKP-0042__add_customer_order_coupon_discount_columns logicalFilePath:EcommerceService
-- comment: Adds CUSTOMER_ORDER.SUBTOTAL_DISCOUNT_AMOUNT/SUBTOTAL_COUPON_CODE/SHIPPING_COUPON_CODE — Phase 2 (checkout integration) of the "ProductDiscount"/Coupon feature, letting a placed order snapshot which coupon(s) were applied and how much they deducted, the same way ORIGINAL_SHIPPING_FEE (DKP-0040) already snapshots a waived shipping fee

-- SUBTOTAL_DISCOUNT_AMOUNT can be added NOT NULL with a constant DEFAULT directly (no separate
-- backfill UPDATE needed, unlike DKP-0040's ORIGINAL_SHIPPING_FEE, which had to backfill from an
-- existing column's actual value) — every order placed before this column existed had no coupon
-- discount applied, so 0 is the historically-correct value, not just a placeholder.
ALTER TABLE ecommerce.CUSTOMER_ORDER
    ADD COLUMN IF NOT EXISTS SUBTOTAL_DISCOUNT_AMOUNT NUMERIC(12, 2) NOT NULL DEFAULT 0;

-- Both nullable — a plain column snapshot of the code actually used, not a foreign key onto
-- COUPON (an order must stay valid/displayable even if the coupon it used is later deleted —
-- though COUPON_IN_USE already blocks that for a coupon with any redemption; this still avoids
-- coupling CUSTOMER_ORDER's own schema to COUPON's lifecycle, same reasoning
-- OrderLine.PRODUCT_VARIANT_ID's plain-column shape already documents for a different pair of
-- tables). Both null means "no coupon applied to that target" — the shopper-facing meaning is the
-- same as SUBTOTAL_DISCOUNT_AMOUNT being zero / SHIPPING_FEE equalling ORIGINAL_SHIPPING_FEE.
ALTER TABLE ecommerce.CUSTOMER_ORDER
    ADD COLUMN IF NOT EXISTS SUBTOTAL_COUPON_CODE VARCHAR(50);

ALTER TABLE ecommerce.CUSTOMER_ORDER
    ADD COLUMN IF NOT EXISTS SHIPPING_COUPON_CODE VARCHAR(50);
