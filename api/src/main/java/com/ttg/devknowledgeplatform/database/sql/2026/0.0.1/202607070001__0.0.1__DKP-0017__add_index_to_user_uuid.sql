-- liquibase formatted sql
-- changeset ttg:202607070001__0.0.1__DKP-0017__add_index_to_user_uuid logicalFilePath:DevKnowledgePlatform
-- comment: Add index to USER.USER_UUID
--
-- DKP-0017: USER_UUID is the lookup key on the hot path — CurrentUserIdArgumentResolver resolves
-- it on every authenticated request, plus UserService.findByUserUuid/findByUserUuidOptional and
-- the friend-graph endpoints that take a userUuid path variable — but it has had no index since
-- it was added in DKP-0004, so every one of those lookups is a sequential scan.
--
-- Plain (non-unique) index, matching IDX_USER_EMAIL/IDX_USER_USERNAME: a UNIQUE constraint was
-- considered (every current generation path — UserServiceImpl, UserSeeder — already uses
-- UUID.randomUUID(), so it would apply cleanly) but deliberately not added here — accepted risk,
-- not an oversight.

CREATE INDEX IF NOT EXISTS IDX_USER_USER_UUID ON product.USER (USER_UUID);
