-- liquibase formatted sql
-- changeset ttg:202606260001__0.0.1__DKP-0008__add_sys_param logicalFilePath:DevKnowledgePlatform
-- comment: Add SYS_PARAM table for general-purpose persistent key-value parameters

-- =============================================================================
-- SYS_PARAM
-- General-purpose key-value store for system-managed persistent parameters.
-- NAME        — a ParamKey enum value (e.g. CENTROID_ARTICLE, ANOMALY_HARD_THRESHOLD).
-- VALUE       — serialized as text: vectors use pgvector notation [f1,f2,...],
--               numeric values are plain decimal strings (e.g. "0.45").
-- COMPUTED_AT — when the value was last computed or refreshed by the application.
--               Distinct from DTE_LAST_MODIFICATION (which records the Liquibase/JPA
--               write time); COMPUTED_AT records when the underlying computation ran.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.SYS_PARAM_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.SYS_PARAM (
    SYS_PARAM_ID            INTEGER                         NOT NULL,
    NAME                    VARCHAR(100)                    NOT NULL,
    VALUE                   TEXT                            NOT NULL,
    COMPUTED_AT             TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_SYS_PARAM PRIMARY KEY (SYS_PARAM_ID)
);

ALTER SEQUENCE product.SYS_PARAM_SEQ OWNED BY product.SYS_PARAM.SYS_PARAM_ID;
