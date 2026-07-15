-- liquibase formatted sql
-- changeset ttg:202606220001__0.0.1__DKP-0006__add_chat_session_tables logicalFilePath:DevKnowledgePlatform
-- comment: Add CHAT_SESSION and CHAT_MESSAGE tables for multi-turn conversation memory

-- =============================================================================
-- CHAT_SESSION
-- Tracks active conversations per user. LAST_ACTIVITY_AT is updated on every
-- Q&A exchange and used to enforce a 24-hour inactivity TTL in the application
-- layer (expired sessions have their messages cleared, not the session row itself,
-- so client-held session IDs remain valid for new conversations).
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.CHAT_SESSION_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.CHAT_SESSION (
    CHAT_SESSION_ID         INTEGER                         NOT NULL,
    USER_ID                 INTEGER                         NOT NULL,
    -- Auto-generated from the first user question (capped at 100 chars by the application layer)
    TITLE                   VARCHAR(500),
    LAST_ACTIVITY_AT        TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_CHAT_SESSION PRIMARY KEY (CHAT_SESSION_ID),

    -- Referential integrity: sessions are owned by a user row.
    -- ON DELETE CASCADE means deleting a user account automatically removes all their sessions.
    CONSTRAINT FK_CHAT_SESSION_USER FOREIGN KEY (USER_ID)
        REFERENCES product.USER (USER_ID) ON DELETE CASCADE
);

ALTER SEQUENCE product.CHAT_SESSION_SEQ OWNED BY product.CHAT_SESSION.CHAT_SESSION_ID;

-- Index for per-user session lookups and ownership checks
CREATE INDEX IF NOT EXISTS IDX_CHAT_SESSION_USER_ID       ON product.CHAT_SESSION (USER_ID);
-- Index for future scheduled cleanup of stale sessions
CREATE INDEX IF NOT EXISTS IDX_CHAT_SESSION_LAST_ACTIVITY ON product.CHAT_SESSION (LAST_ACTIVITY_AT);

-- =============================================================================
-- CHAT_MESSAGE
-- One row per message (USER or ASSISTANT) within a session.
-- TURN_INDEX is a 0-based monotonically increasing counter scoped to the session:
--   index N   = USER question
--   index N+1 = ASSISTANT answer
-- Using an integer index rather than timestamps avoids clock-skew ordering issues
-- and makes "fetch last N messages" a simple ORDER BY + LIMIT query.
-- =============================================================================

CREATE SEQUENCE IF NOT EXISTS product.CHAT_MESSAGE_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.CHAT_MESSAGE (
    CHAT_MESSAGE_ID         INTEGER                         NOT NULL,
    CHAT_SESSION_ID         INTEGER                         NOT NULL,
    ROLE                    VARCHAR(20)                     NOT NULL,
    CONTENT                 TEXT                            NOT NULL,
    TURN_INDEX              INTEGER                         NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_CHAT_MESSAGE PRIMARY KEY (CHAT_MESSAGE_ID),

    CONSTRAINT FK_CHAT_MESSAGE_SESSION FOREIGN KEY (CHAT_SESSION_ID)
        REFERENCES product.CHAT_SESSION (CHAT_SESSION_ID),

    -- Keeps the column type honest; application-layer enum is the source of truth
    CONSTRAINT CKC_CHAT_MESSAGE_ROLE CHECK (ROLE IN ('USER', 'ASSISTANT'))
);

ALTER SEQUENCE product.CHAT_MESSAGE_SEQ OWNED BY product.CHAT_MESSAGE.CHAT_MESSAGE_ID;

-- Lookup by session (JOIN, ownership check)
CREATE INDEX IF NOT EXISTS IDX_CHAT_MESSAGE_SESSION_ID ON product.CHAT_MESSAGE (CHAT_SESSION_ID);
-- Supports ORDER BY CHAT_SESSION_ID, TURN_INDEX for both ASC and DESC fetches
CREATE INDEX IF NOT EXISTS IDX_CHAT_MESSAGE_TURN_ORDER ON product.CHAT_MESSAGE (CHAT_SESSION_ID, TURN_INDEX);
