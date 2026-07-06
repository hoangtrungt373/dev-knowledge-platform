-- liquibase formatted sql
-- changeset ttg:202607060001__0.0.1__DKP-0015__add_friend_management_tables logicalFilePath:DevKnowledgePlatform
-- comment: Friend graph — requests, friendships, and blocking

-- =============================================================================
-- FRIEND_REQUEST
-- =============================================================================
-- Blocking is orthogonal to friendship (a stranger can be blocked; blocking a friend must
-- kill the friendship AND future requests), so each concern gets its own table instead of a
-- single overloaded status column — see docs/PROJECT_STRUCTURE.md for the full rationale.

CREATE SEQUENCE IF NOT EXISTS product.FRIEND_REQUEST_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.FRIEND_REQUEST (
    FRIEND_REQUEST_ID       INTEGER                         NOT NULL,
    REQUESTER_ID            INTEGER                         NOT NULL,
    ADDRESSEE_ID            INTEGER                         NOT NULL,
    STATUS                  VARCHAR(50)                     NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_FRIEND_REQUEST PRIMARY KEY (FRIEND_REQUEST_ID),

    CONSTRAINT FK_FRIEND_REQUEST_REQUESTER FOREIGN KEY (REQUESTER_ID) REFERENCES product.USER (USER_ID),
    CONSTRAINT FK_FRIEND_REQUEST_ADDRESSEE FOREIGN KEY (ADDRESSEE_ID) REFERENCES product.USER (USER_ID),

    CONSTRAINT CKC_FRIEND_REQUEST_STATUS CHECK (STATUS IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'))
);

ALTER SEQUENCE product.FRIEND_REQUEST_SEQ OWNED BY product.FRIEND_REQUEST.FRIEND_REQUEST_ID;

CREATE INDEX IF NOT EXISTS IDX_FRIEND_REQUEST_REQUESTER ON product.FRIEND_REQUEST (REQUESTER_ID);
CREATE INDEX IF NOT EXISTS IDX_FRIEND_REQUEST_ADDRESSEE ON product.FRIEND_REQUEST (ADDRESSEE_ID);

-- Partial unique index: only PENDING rows are constrained, so a rejected/cancelled request
-- doesn't block a later re-request between the same pair. LEAST/GREATEST normalize direction
-- so a pending request can't exist "twice" as A->B and B->A simultaneously.
CREATE UNIQUE INDEX IF NOT EXISTS IDX_FRIEND_REQUEST_PENDING_PAIR
    ON product.FRIEND_REQUEST (LEAST(REQUESTER_ID, ADDRESSEE_ID), GREATEST(REQUESTER_ID, ADDRESSEE_ID))
    WHERE STATUS = 'PENDING';

-- =============================================================================
-- FRIENDSHIP
-- =============================================================================
-- Stored once per pair, canonically ordered (USER_ID_1 < USER_ID_2) so neither "who friended
-- whom" nor lookup direction matters — a lookup by either user always hits the same row.

CREATE SEQUENCE IF NOT EXISTS product.FRIENDSHIP_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.FRIENDSHIP (
    FRIENDSHIP_ID           INTEGER                         NOT NULL,
    USER_ID_1               INTEGER                         NOT NULL,
    USER_ID_2               INTEGER                         NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_FRIENDSHIP PRIMARY KEY (FRIENDSHIP_ID),

    CONSTRAINT FK_FRIENDSHIP_USER_1 FOREIGN KEY (USER_ID_1) REFERENCES product.USER (USER_ID),
    CONSTRAINT FK_FRIENDSHIP_USER_2 FOREIGN KEY (USER_ID_2) REFERENCES product.USER (USER_ID),

    CONSTRAINT CKC_FRIENDSHIP_ORDER CHECK (USER_ID_1 < USER_ID_2)
);

ALTER SEQUENCE product.FRIENDSHIP_SEQ OWNED BY product.FRIENDSHIP.FRIENDSHIP_ID;

CREATE UNIQUE INDEX IF NOT EXISTS IDX_FRIENDSHIP_PAIR ON product.FRIENDSHIP (USER_ID_1, USER_ID_2);
CREATE INDEX IF NOT EXISTS IDX_FRIENDSHIP_USER_1 ON product.FRIENDSHIP (USER_ID_1);
CREATE INDEX IF NOT EXISTS IDX_FRIENDSHIP_USER_2 ON product.FRIENDSHIP (USER_ID_2);

-- =============================================================================
-- USER_BLOCK
-- =============================================================================
-- Directional and independent of FRIENDSHIP/FRIEND_REQUEST — blocking a stranger who never
-- sent a request must work the same as blocking an existing friend.

CREATE SEQUENCE IF NOT EXISTS product.USER_BLOCK_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.USER_BLOCK (
    USER_BLOCK_ID           INTEGER                         NOT NULL,
    BLOCKER_ID              INTEGER                         NOT NULL,
    BLOCKED_ID              INTEGER                         NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_USER_BLOCK PRIMARY KEY (USER_BLOCK_ID),

    CONSTRAINT FK_USER_BLOCK_BLOCKER FOREIGN KEY (BLOCKER_ID) REFERENCES product.USER (USER_ID),
    CONSTRAINT FK_USER_BLOCK_BLOCKED FOREIGN KEY (BLOCKED_ID) REFERENCES product.USER (USER_ID),

    CONSTRAINT CKC_USER_BLOCK_NOT_SELF CHECK (BLOCKER_ID <> BLOCKED_ID)
);

ALTER SEQUENCE product.USER_BLOCK_SEQ OWNED BY product.USER_BLOCK.USER_BLOCK_ID;

CREATE UNIQUE INDEX IF NOT EXISTS IDX_USER_BLOCK_PAIR ON product.USER_BLOCK (BLOCKER_ID, BLOCKED_ID);
CREATE INDEX IF NOT EXISTS IDX_USER_BLOCK_BLOCKER ON product.USER_BLOCK (BLOCKER_ID);
CREATE INDEX IF NOT EXISTS IDX_USER_BLOCK_BLOCKED ON product.USER_BLOCK (BLOCKED_ID);
