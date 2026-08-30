-- liquibase formatted sql
-- changeset ttg:202608300002__0.0.2__DKP-0036__add_customer_order_owner_uuid_index logicalFilePath:EcommerceService
-- comment: Epic 3 Phase 5 (US-3.5) — "list my orders" query needs an index on OWNER_UUID, deliberately not added back in Epic 2 (DKP-0034) since no such query existed yet

CREATE INDEX IF NOT EXISTS IDX_CUSTOMER_ORDER_OWNER_UUID ON ecommerce.CUSTOMER_ORDER (OWNER_UUID);
