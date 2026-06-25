-- DKP-0007 — Add rolling summary column to CHAT_SESSION
--
-- TEXT is chosen over VARCHAR(n) because the summary length depends on how many
-- turns were compressed; a hard cap would truncate summaries for long sessions.
-- PostgreSQL stores TEXT and VARCHAR(n) identically — there is no performance
-- difference — so TEXT is the safe default when the upper bound is unknown.
--
-- No index is created: the column is only read and written by its session's row;
-- it is never used in WHERE or ORDER BY clauses.

ALTER TABLE product.CHAT_SESSION
    ADD COLUMN SUMMARY TEXT;
