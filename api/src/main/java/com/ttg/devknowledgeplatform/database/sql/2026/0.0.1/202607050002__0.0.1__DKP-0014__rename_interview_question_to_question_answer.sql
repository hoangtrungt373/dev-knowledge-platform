-- liquibase formatted sql
-- changeset ttg:202607050002__0.0.1__DKP-0014__rename_interview_question_to_question_answer logicalFilePath:DevKnowledgePlatform
-- comment: Rename INTERVIEW_QUESTION to QUESTION_ANSWER and broaden difficulty/isCommon to nullable
--
-- DKP-0014: INTERVIEW_QUESTION -> QUESTION_ANSWER.
--
-- Design rationale:
--   This content type is retrieved and used for general dev-knowledge Q&A, not just interview
--   prep — the "interview question" name overstated its actual scope. Renaming the table/type
--   to QUESTION_ANSWER, and dropping the NOT NULL constraint on DIFFICULTY/IS_COMMON, lets those
--   two columns stay purely optional interview-specific metadata (populated when a question
--   genuinely has that framing, null otherwise) rather than forcing every general-knowledge
--   question through an interview-difficulty/frequency judgment call that doesn't apply to it.
--
--   The existing DIFFICULTY CHECK constraint (CHECK (DIFFICULTY IN (...))) already permits NULL
--   under standard SQL three-valued logic — a NULL operand makes the whole expression evaluate
--   to NULL (unknown), and Postgres treats a NULL CHECK result as satisfied, not violated — so
--   only the NOT NULL constraint itself needs dropping, not the CHECK expression.
--
--   CONTENT_ITEM.TYPE and CONTENT_EMBEDDING.SOURCE_TYPE CHECK constraints must be dropped and
--   re-added (Postgres has no ALTER CHECK CONSTRAINT), and any existing rows are updated first
--   so the stricter re-added constraint doesn't reject pre-existing data.

-- Update any existing rows before tightening constraints back up.
UPDATE product.CONTENT_ITEM SET TYPE = 'QUESTION_ANSWER' WHERE TYPE = 'INTERVIEW_QUESTION';
UPDATE product.CONTENT_EMBEDDING SET SOURCE_TYPE = 'QUESTION_ANSWER' WHERE SOURCE_TYPE = 'INTERVIEW_QUESTION';
UPDATE product.SYS_PARAM SET NAME = 'CENTROID_QUESTION_ANSWER' WHERE NAME = 'CENTROID_INTERVIEW_QUESTION';

-- Rename the sequence, table, and PK column.
ALTER SEQUENCE product.INTERVIEW_QUESTION_SEQ RENAME TO QUESTION_ANSWER_SEQ;
ALTER TABLE product.INTERVIEW_QUESTION RENAME TO QUESTION_ANSWER;
ALTER TABLE product.QUESTION_ANSWER RENAME COLUMN INTERVIEW_QUESTION_ID TO QUESTION_ANSWER_ID;

-- Rename constraints and indexes to match.
ALTER TABLE product.QUESTION_ANSWER RENAME CONSTRAINT PK_INTERVIEW_QUESTION TO PK_QUESTION_ANSWER;
ALTER TABLE product.QUESTION_ANSWER RENAME CONSTRAINT FK_INTERVIEW_QUESTION_CONTENT TO FK_QUESTION_ANSWER_CONTENT;
ALTER TABLE product.QUESTION_ANSWER RENAME CONSTRAINT CKC_INTERVIEW_QUESTION_DIFFICULTY TO CKC_QUESTION_ANSWER_DIFFICULTY;
ALTER INDEX product.IDX_INTERVIEW_QUESTION_CONTENT RENAME TO IDX_QUESTION_ANSWER_CONTENT;
ALTER INDEX product.IDX_INTERVIEW_QUESTION_DIFFICULTY RENAME TO IDX_QUESTION_ANSWER_DIFFICULTY;

-- Broaden DIFFICULTY and IS_COMMON to optional.
ALTER TABLE product.QUESTION_ANSWER ALTER COLUMN DIFFICULTY DROP NOT NULL;
ALTER TABLE product.QUESTION_ANSWER ALTER COLUMN IS_COMMON DROP NOT NULL;

-- Update the CONTENT_ITEM.TYPE and CONTENT_EMBEDDING.SOURCE_TYPE value lists
-- (Postgres has no ALTER CHECK CONSTRAINT — drop and re-add).
ALTER TABLE product.CONTENT_ITEM DROP CONSTRAINT CKC_CONTENT_ITEM_TYPE;
ALTER TABLE product.CONTENT_ITEM
    ADD CONSTRAINT CKC_CONTENT_ITEM_TYPE CHECK (TYPE IN ('QUESTION_ANSWER', 'ARTICLE', 'BLOG_POST'));

ALTER TABLE product.CONTENT_EMBEDDING DROP CONSTRAINT CKC_CONTENT_EMBEDDING_SOURCE_TYPE;
ALTER TABLE product.CONTENT_EMBEDDING
    ADD CONSTRAINT CKC_CONTENT_EMBEDDING_SOURCE_TYPE CHECK (SOURCE_TYPE IN ('QUESTION_ANSWER', 'ARTICLE', 'BLOG_POST'));
