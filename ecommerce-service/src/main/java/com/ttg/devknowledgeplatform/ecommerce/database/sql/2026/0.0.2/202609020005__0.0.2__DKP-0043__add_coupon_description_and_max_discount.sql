-- liquibase formatted sql
-- changeset ttg:202609020005__0.0.2__DKP-0043__add_coupon_description_and_max_discount logicalFilePath:EcommerceService
-- comment: Adds COUPON.DESCRIPTION (a shopper-facing summary, e.g. "20% off orders over $100, up to $20" — for the future gui coupon-picker dialog) and COUPON.MAX_DISCOUNT_AMOUNT (an optional cap on a single redemption's discount, independent of MIN_SUBTOTAL/VALUE — the "20% off, capped at $20" shape a plain percentage-of-subtotal calculation can't express on its own)

ALTER TABLE ecommerce.COUPON
    ADD COLUMN IF NOT EXISTS DESCRIPTION VARCHAR(255),
    ADD COLUMN IF NOT EXISTS MAX_DISCOUNT_AMOUNT NUMERIC(12, 2);

-- DESCRIPTION is nullable, deliberately not backfilled/required — purely presentational (see
-- CouponResponse's own Javadoc), unlike every other COUPON column, which all gate real checkout
-- behavior. A coupon created before this migration (or via CouponSeeder before its own CSV gained
-- a description column) simply has none; CouponRedemptionService never reads it.
--
-- MAX_DISCOUNT_AMOUNT is nullable too — no cap unless the admin sets one. Applies uniformly to
-- both CouponType values in CouponRedemptionServiceImpl.calculateDiscount (computed raw discount,
-- then clamped to this cap if set, then clamped again to the base amount) rather than being
-- restricted to PERCENTAGE only — a FIXED_AMOUNT coupon capped below its own VALUE is a valid,
-- if unusual, admin choice (further reducing an already-fixed discount), not a state worth
-- rejecting.
ALTER TABLE ecommerce.COUPON
    ADD CONSTRAINT CKC_COUPON_MAX_DISCOUNT_POSITIVE
        CHECK (MAX_DISCOUNT_AMOUNT IS NULL OR MAX_DISCOUNT_AMOUNT > 0);
