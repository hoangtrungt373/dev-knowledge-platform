-- liquibase formatted sql
-- changeset ttg:202609020007__0.0.2__DKP-0045__add_address_phone_column logicalFilePath:EcommerceService
-- comment: Adds a PHONE column to CUSTOMER_ORDER (the embedded Address value object) and SAVED_ADDRESS (the AddressBook entity) — a shipping contact number, required going forward for every fresh write (see AddressRequest/Create+UpdateSavedAddressRequest) but nullable at the DB level since pre-existing rows have nothing to backfill it from

-- Nullable, unlike every sibling address column (FULL_NAME/LINE_1/etc, all NOT NULL from the table's
-- very first migration) — those never had this problem because there were no existing rows yet when
-- they were added. There is no historically-correct value to backfill here the way DKP-0042's
-- SUBTOTAL_DISCOUNT_AMOUNT could default to 0 for pre-coupon orders, so this can only be enforced
-- as "required" at the application layer (imperative validation for a fresh checkout address,
-- @NotBlank for AddressBook writes) — never as a DB constraint, without discarding real history.
ALTER TABLE ecommerce.CUSTOMER_ORDER
    ADD COLUMN IF NOT EXISTS PHONE VARCHAR(30);

ALTER TABLE ecommerce.SAVED_ADDRESS
    ADD COLUMN IF NOT EXISTS PHONE VARCHAR(30);
