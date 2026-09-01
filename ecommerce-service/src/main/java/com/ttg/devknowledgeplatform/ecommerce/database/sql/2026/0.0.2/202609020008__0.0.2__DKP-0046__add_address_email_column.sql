-- liquibase formatted sql
-- changeset ttg:202609020008__0.0.2__DKP-0046__add_address_email_column logicalFilePath:EcommerceService
-- comment: Adds an EMAIL column to CUSTOMER_ORDER (the embedded Address value object) and SAVED_ADDRESS (the AddressBook entity) — the invoice/order-confirmation recipient, deliberately independent of the caller's Keycloak login email since the two can legitimately differ, per request; required going forward for every fresh write but nullable at the DB level since pre-existing rows have nothing to backfill it from

-- Same "nullable at the DB level, required at the application layer" shape as DKP-0045's PHONE
-- column, and for the identical reason — pre-existing CUSTOMER_ORDER/SAVED_ADDRESS rows have no
-- email on file, so there is no historically-correct value to backfill. VARCHAR(255) matches this
-- reactor's own existing EMAIL column convention (identity.USER.EMAIL, social.PROFILE.EMAIL).
ALTER TABLE ecommerce.CUSTOMER_ORDER
    ADD COLUMN IF NOT EXISTS EMAIL VARCHAR(255);

ALTER TABLE ecommerce.SAVED_ADDRESS
    ADD COLUMN IF NOT EXISTS EMAIL VARCHAR(255);
