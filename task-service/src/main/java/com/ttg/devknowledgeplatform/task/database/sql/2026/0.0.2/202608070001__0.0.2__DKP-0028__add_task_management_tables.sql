-- liquibase formatted sql
-- changeset ttg:202608070001__0.0.2__DKP-0028__add_task_management_tables logicalFilePath:TaskService
-- comment: Add task-service's own PROJECT/TASK tables in a new `task` schema
--
-- Ownership is a plain OWNER_UUID column (the Keycloak JWT's `sub` claim), not a foreign key to a
-- local USER table — this module resolves "who is the caller" straight from the verified JWT via
-- KeycloakJwtAuthenticationConverter (no persistence, mirrors ecommerce-service's converter)
-- rather than JIT-provisioning its own User copy the way gateway/identity-service do. Every
-- ownership check this module does only ever compares two UUIDs ("is this row's owner the
-- caller") — it never needs to display another user's profile, so there's no cross-service User
-- duplication to justify (see task-service/CLAUDE.md and the project-microservices-extraction-plan
-- memory's "Option C" discussion).
--
-- Table shape (minus the owner column) is a fresh snapshot of gateway's product.PROJECT/
-- product.TASK as of DKP-0022 (init_tables through remove_content_item_id_from_task), not a
-- replay of that migration history. In particular this skips product.TASK.CONTENT_ITEM_ID
-- entirely: it was already removed from the monolith by DKP-0022 before this table ever existed
-- here, and task-service has no Maven dependency on content-service (see task-service/CLAUDE.md).

CREATE SCHEMA IF NOT EXISTS task;

CREATE SEQUENCE IF NOT EXISTS task.PROJECT_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS task.PROJECT (
    PROJECT_ID              INTEGER                         NOT NULL,
    NAME                    VARCHAR(255)                    NOT NULL,
    DESCRIPTION             TEXT,
    OWNER_UUID              VARCHAR(36)                     NOT NULL,
    STATUS                  VARCHAR(50)                     NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_PROJECT PRIMARY KEY (PROJECT_ID),
    CONSTRAINT CKC_PROJECT_STATUS CHECK (STATUS IN ('ACTIVE','ARCHIVED'))
);

ALTER SEQUENCE task.PROJECT_SEQ OWNED BY task.PROJECT.PROJECT_ID;

CREATE INDEX IF NOT EXISTS IDX_PROJECT_OWNER ON task.PROJECT (OWNER_UUID);

CREATE SEQUENCE IF NOT EXISTS task.TASK_SEQ
    START WITH 1 INCREMENT BY 50 NO MAXVALUE NO CYCLE;

CREATE TABLE IF NOT EXISTS task.TASK (
    TASK_ID                 INTEGER                         NOT NULL,
    PROJECT_ID              INTEGER,
    OWNER_UUID              VARCHAR(36)                     NOT NULL,
    TITLE                   VARCHAR(255)                    NOT NULL,
    DESCRIPTION             TEXT,
    STATUS                  VARCHAR(50)                     NOT NULL,
    PRIORITY                VARCHAR(50)                     NOT NULL,
    DUE_DATE                TIMESTAMP WITH TIME ZONE,
    PARENT_TASK_ID          INTEGER,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_TASK PRIMARY KEY (TASK_ID),
    CONSTRAINT FK_TASK_PROJECT FOREIGN KEY (PROJECT_ID) REFERENCES task.PROJECT (PROJECT_ID),
    CONSTRAINT FK_TASK_PARENT FOREIGN KEY (PARENT_TASK_ID) REFERENCES task.TASK (TASK_ID),
    CONSTRAINT CKC_TASK_STATUS CHECK (STATUS IN ('TODO','IN_PROGRESS','DONE')),
    CONSTRAINT CKC_TASK_PRIORITY CHECK (PRIORITY IN ('LOW','MEDIUM','HIGH','URGENT'))
);

ALTER SEQUENCE task.TASK_SEQ OWNED BY task.TASK.TASK_ID;

CREATE INDEX IF NOT EXISTS IDX_TASK_PROJECT ON task.TASK (PROJECT_ID);
CREATE INDEX IF NOT EXISTS IDX_TASK_OWNER ON task.TASK (OWNER_UUID);
CREATE INDEX IF NOT EXISTS IDX_TASK_STATUS ON task.TASK (STATUS);
CREATE INDEX IF NOT EXISTS IDX_TASK_PARENT ON task.TASK (PARENT_TASK_ID);
