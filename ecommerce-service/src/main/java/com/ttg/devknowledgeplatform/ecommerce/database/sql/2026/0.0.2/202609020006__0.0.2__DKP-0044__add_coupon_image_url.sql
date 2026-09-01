-- liquibase formatted sql
-- changeset ttg:202609020006__0.0.2__DKP-0044__add_coupon_image_url logicalFilePath:EcommerceService
-- comment: Adds COUPON.IMAGE_URL — a permanent, unsigned image URL (e.g. a promo banner/icon) for the future gui coupon-picker dialog, uploaded via infra's StorageService.uploadPublicImage same as ProductDescriptionImageService already does for Product.description's inline images

ALTER TABLE ecommerce.COUPON
    ADD COLUMN IF NOT EXISTS IMAGE_URL VARCHAR(500);

-- Nullable, purely presentational (same as DESCRIPTION, added in DKP-0043) — a coupon created
-- before this migration, or one an admin simply never bothered to illustrate, has none.
-- Deliberately a *permanent* URL (StorageService.uploadPublicImage), not the time-limited
-- presigned kind ProductImage/avatars use: a Coupon has no "not-yet-published/deactivated"
-- access-control concern the way a Product does (see StorageService's own Javadoc for that
-- distinction) — a coupon's whole purpose is being shown to shoppers, so there's nothing here a
-- permanent public link would leak that isn't already meant to be public.
