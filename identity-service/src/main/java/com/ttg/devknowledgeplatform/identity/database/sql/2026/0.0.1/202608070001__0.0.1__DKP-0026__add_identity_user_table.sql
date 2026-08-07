-- liquibase formatted sql
-- changeset ttg:202608070001__0.0.1__DKP-0026__add_identity_user_table logicalFilePath:IdentityService
-- comment: Add identity-service's own USER table in a new `identity` schema

-- =============================================================================
-- identity schema — this service's own, following extraction from the monolith's
-- shared `product` schema. common.entity.User's @Table no longer hardcodes a
-- schema (see that class's Javadoc) specifically so each standalone deployable
-- (gateway/ecommerce-service/identity-service) can point it at its own schema via
-- its own `hibernate.default_schema` property — this is that schema for this app.
--
-- This table's shape is a fresh snapshot of gateway's product.USER as of DKP-0025
-- (init_tables through add_keycloak_subject_id_to_user), not a replay of that
-- migration history — same approach ecommerce-service's DKP-0023 took for its own
-- tables. gateway's product.USER and this identity.USER are two independent
-- physical tables from this point on; each app's own KeycloakJwtAuthenticationConverter
-- JIT-provisions its own copy of a given Keycloak identity into its own table.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS identity;

CREATE SEQUENCE IF NOT EXISTS identity.USER_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS identity.USER (
    USER_ID                 INTEGER                         NOT NULL,
    USER_UUID               VARCHAR(36)                     NOT NULL,
    EMAIL                   VARCHAR(255)                    NOT NULL,
    USERNAME                VARCHAR(255)                    NOT NULL,
    PASSWORD                VARCHAR(255)                    NOT NULL,
    FIRST_NAME              VARCHAR(255),
    LAST_NAME               VARCHAR(255),
    PROFILE_PICTURE         VARCHAR(500),
    PROVIDER                VARCHAR(50)                     NOT NULL,
    PROVIDER_ID             VARCHAR(255),
    ROLE                    VARCHAR(50)                     NOT NULL,
    EMAIL_VERIFIED          BOOLEAN                         NOT NULL,
    STATUS                  VARCHAR(50)                     NOT NULL,
    ENABLED                 BOOLEAN                         NOT NULL,
    SEED_ID                 VARCHAR(100),
    KEYCLOAK_SUBJECT_ID     VARCHAR(255),
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_USER PRIMARY KEY (USER_ID),

    CONSTRAINT CKC_USER_PROVIDER CHECK (PROVIDER IN ('LOCAL', 'GOOGLE', 'FACEBOOK')),
    CONSTRAINT CKC_USER_STATUS CHECK (STATUS IN ('ONLINE', 'OFFLINE', 'AWAY', 'BUSY')),
    CONSTRAINT CKC_USER_ROLE CHECK (ROLE IN ('USER', 'ADMIN'))
);

ALTER SEQUENCE identity.USER_SEQ OWNED BY identity.USER.USER_ID;

CREATE INDEX IF NOT EXISTS IDX_USER_USER_UUID ON identity.USER (USER_UUID);

CREATE UNIQUE INDEX IF NOT EXISTS UX_USER_EMAIL ON identity.USER (EMAIL);
ALTER TABLE identity.USER ADD CONSTRAINT UK_USER_EMAIL UNIQUE USING INDEX UX_USER_EMAIL;

CREATE UNIQUE INDEX IF NOT EXISTS UX_USER_USERNAME ON identity.USER (USERNAME);
ALTER TABLE identity.USER ADD CONSTRAINT UK_USER_USERNAME UNIQUE USING INDEX UX_USER_USERNAME;

CREATE UNIQUE INDEX IF NOT EXISTS UX_USER_PROVIDER_PROVIDER_ID ON identity.USER (PROVIDER, PROVIDER_ID);
ALTER TABLE identity.USER
    ADD CONSTRAINT UK_USER_PROVIDER_PROVIDER_ID UNIQUE USING INDEX UX_USER_PROVIDER_PROVIDER_ID;

CREATE UNIQUE INDEX IF NOT EXISTS UX_USER_SEED_ID ON identity.USER (SEED_ID);

ALTER TABLE identity.USER ADD CONSTRAINT UK_USER_KEYCLOAK_SUBJECT_ID UNIQUE (KEYCLOAK_SUBJECT_ID);
