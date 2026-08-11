-- liquibase formatted sql
-- changeset ttg:202608070001__0.0.2__DKP-0029__add_social_profile_table logicalFilePath:SocialService
-- comment: Add social-service's own lean PROFILE table in a new `social` schema
--
-- social schema — this service's own, following extraction from the monolith's shared `product`
-- schema, same per-service-per-schema convention as ecommerce/identity/task-service.
--
-- Unlike gateway's product.USER/identity-service's identity.USER, this is deliberately NOT a full
-- snapshot of common.entity.User's column set — this module never reuses that shared entity at all
-- (see entity.SocialProfile's Javadoc). Every column here is one this module's own code actually
-- reads or writes: no PASSWORD, PROVIDER/PROVIDER_ID, ROLE, EMAIL_VERIFIED, or ENABLED — this
-- module has no auth-lifecycle concern and nothing here is ever branched on by role. This avoids
-- the two-services-coupled-to-one-shared-shape problem a full User snapshot would otherwise carry
-- forward into a module that doesn't need most of it.

CREATE SCHEMA IF NOT EXISTS social;

CREATE SEQUENCE IF NOT EXISTS social.PROFILE_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS social.PROFILE (
    PROFILE_ID              INTEGER                         NOT NULL,
    PROFILE_UUID            VARCHAR(36)                     NOT NULL,
    USERNAME                VARCHAR(255)                    NOT NULL,
    EMAIL                   VARCHAR(255)                    NOT NULL,
    FIRST_NAME              VARCHAR(255),
    LAST_NAME               VARCHAR(255),
    PROFILE_PICTURE         VARCHAR(500),
    STATUS                  VARCHAR(50)                     NOT NULL,
    KEYCLOAK_SUBJECT_ID     VARCHAR(255),
    SEED_ID                 VARCHAR(100),
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_PROFILE PRIMARY KEY (PROFILE_ID),

    CONSTRAINT CKC_PROFILE_STATUS CHECK (STATUS IN ('ONLINE', 'OFFLINE', 'AWAY', 'BUSY'))
);

ALTER SEQUENCE social.PROFILE_SEQ OWNED BY social.PROFILE.PROFILE_ID;

CREATE INDEX IF NOT EXISTS IDX_PROFILE_PROFILE_UUID ON social.PROFILE (PROFILE_UUID);

CREATE UNIQUE INDEX IF NOT EXISTS UX_PROFILE_EMAIL ON social.PROFILE (EMAIL);
ALTER TABLE social.PROFILE ADD CONSTRAINT UK_PROFILE_EMAIL UNIQUE USING INDEX UX_PROFILE_EMAIL;

CREATE UNIQUE INDEX IF NOT EXISTS UX_PROFILE_USERNAME ON social.PROFILE (USERNAME);
ALTER TABLE social.PROFILE ADD CONSTRAINT UK_PROFILE_USERNAME UNIQUE USING INDEX UX_PROFILE_USERNAME;

CREATE UNIQUE INDEX IF NOT EXISTS UX_PROFILE_SEED_ID ON social.PROFILE (SEED_ID);

ALTER TABLE social.PROFILE ADD CONSTRAINT UK_PROFILE_KEYCLOAK_SUBJECT_ID UNIQUE (KEYCLOAK_SUBJECT_ID);
