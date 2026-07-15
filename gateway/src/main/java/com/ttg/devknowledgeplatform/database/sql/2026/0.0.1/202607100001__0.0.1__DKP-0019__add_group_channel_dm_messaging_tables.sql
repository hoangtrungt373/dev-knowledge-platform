-- liquibase formatted sql
-- changeset ttg:202607100001__0.0.1__DKP-0019__add_group_channel_dm_messaging_tables logicalFilePath:DevKnowledgePlatform
-- comment: Chat feature MVP — groups, channels, 1:1 DM threads, and their messages

-- =============================================================================
-- MESSAGE_GROUP
-- =============================================================================
-- Table is named MESSAGE_GROUP rather than GROUP — GROUP is a reserved word in PostgreSQL
-- (used by GROUP BY) and would require quoting in every raw query.
-- No OWNER_ID column here: the owner is whichever GROUP_MEMBER row holds ROLE = 'OWNER', so
-- there is exactly one source of truth for it rather than a duplicated reference that could
-- drift out of sync.

CREATE SEQUENCE IF NOT EXISTS product.MESSAGE_GROUP_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.MESSAGE_GROUP (
    GROUP_ID                INTEGER                         NOT NULL,
    NAME                    VARCHAR(255)                    NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_MESSAGE_GROUP PRIMARY KEY (GROUP_ID)
);

ALTER SEQUENCE product.MESSAGE_GROUP_SEQ OWNED BY product.MESSAGE_GROUP.GROUP_ID;

-- =============================================================================
-- GROUP_MEMBER
-- =============================================================================
-- One row per (group, user) pair, carrying the member's role. Open add: any user can be
-- added by an owner/admin, no accepted FRIENDSHIP required (unlike DM_THREAD below).

CREATE SEQUENCE IF NOT EXISTS product.GROUP_MEMBER_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.GROUP_MEMBER (
    GROUP_MEMBER_ID         INTEGER                         NOT NULL,
    GROUP_ID                INTEGER                         NOT NULL,
    USER_ID                 INTEGER                         NOT NULL,
    ROLE                    VARCHAR(20)                     NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_GROUP_MEMBER PRIMARY KEY (GROUP_MEMBER_ID),

    CONSTRAINT FK_GROUP_MEMBER_GROUP FOREIGN KEY (GROUP_ID)
        REFERENCES product.MESSAGE_GROUP (GROUP_ID) ON DELETE CASCADE,
    CONSTRAINT FK_GROUP_MEMBER_USER FOREIGN KEY (USER_ID)
        REFERENCES product.USER (USER_ID),

    CONSTRAINT CKC_GROUP_MEMBER_ROLE CHECK (ROLE IN ('OWNER', 'ADMIN', 'MEMBER'))
);

ALTER SEQUENCE product.GROUP_MEMBER_SEQ OWNED BY product.GROUP_MEMBER.GROUP_MEMBER_ID;

CREATE UNIQUE INDEX IF NOT EXISTS UK_GROUP_MEMBER_GROUP_USER ON product.GROUP_MEMBER (GROUP_ID, USER_ID);
CREATE INDEX IF NOT EXISTS IDX_GROUP_MEMBER_USER ON product.GROUP_MEMBER (USER_ID);

-- =============================================================================
-- CHANNEL
-- =============================================================================
-- Every group member can see every channel in this MVP — no private/restricted channel
-- concept yet. Name uniqueness is scoped per group, not global.

CREATE SEQUENCE IF NOT EXISTS product.CHANNEL_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.CHANNEL (
    CHANNEL_ID              INTEGER                         NOT NULL,
    GROUP_ID                INTEGER                         NOT NULL,
    NAME                    VARCHAR(100)                    NOT NULL,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_CHANNEL PRIMARY KEY (CHANNEL_ID),

    CONSTRAINT FK_CHANNEL_GROUP FOREIGN KEY (GROUP_ID)
        REFERENCES product.MESSAGE_GROUP (GROUP_ID) ON DELETE CASCADE
);

ALTER SEQUENCE product.CHANNEL_SEQ OWNED BY product.CHANNEL.CHANNEL_ID;

CREATE UNIQUE INDEX IF NOT EXISTS UK_CHANNEL_GROUP_NAME ON product.CHANNEL (GROUP_ID, NAME);

-- =============================================================================
-- DM_THREAD
-- =============================================================================
-- Stored once per pair, canonically ordered (USER_ID_1 < USER_ID_2) — same convention as
-- FRIENDSHIP.CKC_FRIENDSHIP_ORDER, so lookup direction never matters. Opening or sending to
-- a thread requires an accepted FRIENDSHIP between the two users; enforced in the service
-- layer, not here, since it's a business rule spanning two tables in two different modules'
-- entities conceptually (FRIENDSHIP already exists independently of chat).

CREATE SEQUENCE IF NOT EXISTS product.DM_THREAD_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.DM_THREAD (
    DM_THREAD_ID            INTEGER                         NOT NULL,
    USER_ID_1               INTEGER                         NOT NULL,
    USER_ID_2               INTEGER                         NOT NULL,
    LAST_MESSAGE_AT         TIMESTAMP WITH TIME ZONE,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_DM_THREAD PRIMARY KEY (DM_THREAD_ID),

    CONSTRAINT FK_DM_THREAD_USER_1 FOREIGN KEY (USER_ID_1) REFERENCES product.USER (USER_ID),
    CONSTRAINT FK_DM_THREAD_USER_2 FOREIGN KEY (USER_ID_2) REFERENCES product.USER (USER_ID),

    CONSTRAINT CKC_DM_THREAD_ORDER CHECK (USER_ID_1 < USER_ID_2)
);

ALTER SEQUENCE product.DM_THREAD_SEQ OWNED BY product.DM_THREAD.DM_THREAD_ID;

CREATE UNIQUE INDEX IF NOT EXISTS UK_DM_THREAD_USER_PAIR ON product.DM_THREAD (USER_ID_1, USER_ID_2);
CREATE INDEX IF NOT EXISTS IDX_DM_THREAD_USER_1 ON product.DM_THREAD (USER_ID_1);
CREATE INDEX IF NOT EXISTS IDX_DM_THREAD_USER_2 ON product.DM_THREAD (USER_ID_2);

-- =============================================================================
-- DM_MESSAGE
-- =============================================================================
-- CONTENT and the ATTACHMENT_* columns are independently nullable — a message can carry
-- text only, an attachment only, or both. ATTACHMENT_OBJECT_KEY is a MinIO object key
-- (resolved to a presigned URL at read time), not a stored URL — same pattern StorageService
-- already uses for avatar images. Ordered by DTE_CREATION (inherited audit column), not an
-- explicit turn-index counter like CHAT_MESSAGE.TURN_INDEX — that counter exists there to
-- guarantee strict single-writer USER/ASSISTANT alternation, which doesn't apply here since
-- either DM participant can write concurrently.

CREATE SEQUENCE IF NOT EXISTS product.DM_MESSAGE_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.DM_MESSAGE (
    DM_MESSAGE_ID           INTEGER                         NOT NULL,
    DM_THREAD_ID            INTEGER                         NOT NULL,
    SENDER_ID               INTEGER                         NOT NULL,
    MESSAGE_TYPE            VARCHAR(20)                     NOT NULL,
    CONTENT                 TEXT,
    ATTACHMENT_OBJECT_KEY   VARCHAR(500),
    ATTACHMENT_MIME_TYPE    VARCHAR(100),
    ATTACHMENT_FILE_NAME    VARCHAR(255),
    ATTACHMENT_FILE_SIZE    BIGINT,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_DM_MESSAGE PRIMARY KEY (DM_MESSAGE_ID),

    CONSTRAINT FK_DM_MESSAGE_THREAD FOREIGN KEY (DM_THREAD_ID)
        REFERENCES product.DM_THREAD (DM_THREAD_ID) ON DELETE CASCADE,
    CONSTRAINT FK_DM_MESSAGE_SENDER FOREIGN KEY (SENDER_ID)
        REFERENCES product.USER (USER_ID),

    CONSTRAINT CKC_DM_MESSAGE_TYPE CHECK (MESSAGE_TYPE IN ('TEXT', 'IMAGE', 'FILE'))
);

ALTER SEQUENCE product.DM_MESSAGE_SEQ OWNED BY product.DM_MESSAGE.DM_MESSAGE_ID;

-- Supports paginated "fetch this thread's history" ordered by recency (US-7); leftmost
-- column also covers plain DM_THREAD_ID lookups, so no separate single-column index needed.
CREATE INDEX IF NOT EXISTS IDX_DM_MESSAGE_THREAD_CREATED ON product.DM_MESSAGE (DM_THREAD_ID, DTE_CREATION);

-- =============================================================================
-- CHANNEL_MESSAGE
-- =============================================================================
-- Field shape mirrors DM_MESSAGE exactly (see notes above) — kept as a separate table
-- rather than unified with DM_MESSAGE under one generic "conversation" concept, matching
-- how FRIENDSHIP/USER_BLOCK are already two separate tables rather than one generic
-- "relationship" table.

CREATE SEQUENCE IF NOT EXISTS product.CHANNEL_MESSAGE_SEQ
    START WITH 1
    INCREMENT BY 50
    NO MAXVALUE
    NO CYCLE;

CREATE TABLE IF NOT EXISTS product.CHANNEL_MESSAGE (
    CHANNEL_MESSAGE_ID      INTEGER                         NOT NULL,
    CHANNEL_ID              INTEGER                         NOT NULL,
    SENDER_ID               INTEGER                         NOT NULL,
    MESSAGE_TYPE            VARCHAR(20)                     NOT NULL,
    CONTENT                 TEXT,
    ATTACHMENT_OBJECT_KEY   VARCHAR(500),
    ATTACHMENT_MIME_TYPE    VARCHAR(100),
    ATTACHMENT_FILE_NAME    VARCHAR(255),
    ATTACHMENT_FILE_SIZE    BIGINT,
    USR_CREATION            VARCHAR(128)                    NOT NULL,
    DTE_CREATION            TIMESTAMP WITH TIME ZONE        NOT NULL,
    USR_LAST_MODIFICATION   VARCHAR(128)                    NOT NULL,
    DTE_LAST_MODIFICATION   TIMESTAMP WITH TIME ZONE        NOT NULL,
    VERSION                 INTEGER                         NOT NULL,

    CONSTRAINT PK_CHANNEL_MESSAGE PRIMARY KEY (CHANNEL_MESSAGE_ID),

    CONSTRAINT FK_CHANNEL_MESSAGE_CHANNEL FOREIGN KEY (CHANNEL_ID)
        REFERENCES product.CHANNEL (CHANNEL_ID) ON DELETE CASCADE,
    CONSTRAINT FK_CHANNEL_MESSAGE_SENDER FOREIGN KEY (SENDER_ID)
        REFERENCES product.USER (USER_ID),

    CONSTRAINT CKC_CHANNEL_MESSAGE_TYPE CHECK (MESSAGE_TYPE IN ('TEXT', 'IMAGE', 'FILE'))
);

ALTER SEQUENCE product.CHANNEL_MESSAGE_SEQ OWNED BY product.CHANNEL_MESSAGE.CHANNEL_MESSAGE_ID;

-- Supports paginated "fetch this channel's history" ordered by recency (US-18); leftmost
-- column also covers plain CHANNEL_ID lookups, so no separate single-column index needed.
CREATE INDEX IF NOT EXISTS IDX_CHANNEL_MESSAGE_CHANNEL_CREATED ON product.CHANNEL_MESSAGE (CHANNEL_ID, DTE_CREATION);
