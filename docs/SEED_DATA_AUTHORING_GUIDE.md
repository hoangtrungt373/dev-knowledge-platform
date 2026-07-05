# Seed Data Authoring Guide

Reference for writing new seed content under `api/src/main/resources/data/`, consumed by
`DataSeedingRunner` (see `api/service/seed/`, `docs/PROJECT_STRUCTURE.md`). Read this before
generating a new batch of questions (or any future content type seeded the same way).

`QuestionAnswer` (formerly named `InterviewQuestion`) is general dev-knowledge Q&A, not only
interview prep — see `CHANGELOG.md` for the rename rationale. `difficulty`/`isCommon` are
optional, interview-specific metadata, not defining characteristics of the content type.

---

## Format decision (why Markdown + front matter for QuestionAnswer)

`Category`/`Tag` stay **CSV** (`data/csv/*.csv`) — genuinely flat, short, tabular data, CSV's
sweet spot.

`QuestionAnswer` uses **one Markdown file per question**, front matter + body
(`data/question-answers/*.md`), not CSV or JSON. Reason: `detailedAnswer` is long,
multi-paragraph markdown with code blocks — CSV requires quoting/escaping that's error-prone by
hand and produces unreadable git diffs (one word changed inside a giant quoted field shows as a
full-blob diff); JSON has no native multi-line string, so the same content becomes one
unreadable `\n`-escaped line. Markdown lets the highest-maintenance field be edited as plain,
unescaped prose with real code-block syntax highlighting, and per-question files make diffs and
reviews meaningful at the individual-question level.

Don't relitigate this per batch — if a genuinely new content type is added later, re-run this
same evaluation against *its* shape rather than assuming CSV or Markdown is always right.

---

## Identity: `id` is the idempotency key, everything else is free to edit

**This seed data is long-lived and coexists permanently with user-created content in the same
tables** — it is not throwaway dev/demo data that gets wiped and reseeded. That single fact
drives the identity design below: whatever field a seeder checks "does this already exist"
against must never change across re-runs, or an edit to that field causes a **duplicate INSERT**
(the seeders are insert-only; they never update an existing row). Early versions of this
mechanism used `name` (Category/Tag) or a title-derived `slug` (QuestionAnswer) for that
check — both are real content fields a human will reasonably want to edit later, which would
have silently duplicated the row on the next seeding run.

The fix: every seed row carries a permanent, seed-file-only **`id`**, persisted as
`Category.seedId` / `Tag.seedId` / `ContentItem.seedId` (DB column `SEED_ID`, nullable, `NULL`
for every user/admin-created row — see Liquibase `DKP-0013`). `id` is the *only* thing a seeder
checks for idempotency. `name`/`title`/`slug` stay completely free to edit going forward without
any risk of a duplicate row appearing on the next run — **and cross-file references use `id`
too** (`categories.csv`'s `parentId`; `QuestionAnswer`'s `categoryId`/`tagIds`), not `name`,
for the same reason: a reference by `name` would silently break the moment that name was edited.

Two things this deliberately does **not** solve, so expectations stay calibrated:
- **PK values are still never authored.** `id` is not `Category.id` — the real integer primary
  key stays sequence-generated (`CATEGORY_SEQ`/etc.) exactly as before. Hardcoding a PK value in
  a seed file would desync that sequence and collide with the next row a real user creates
  through the app.
- **Editing a file still doesn't update the existing DB row.** An `id` match means "skip, already
  seeded" — it does not re-apply changes to `name`/`title`/body/category/tags. That would require
  real update-in-place logic, which is a separate, larger feature this mechanism doesn't attempt.

## File schema

`data/csv/categories.csv` — columns: `id, name, parentId` (empty `parentId` = root category;
`parentId` references another row's `id`, not its name; parent rows must precede their children,
since a child's parent is resolved by `id` lookup against already-persisted rows in the same pass).

`data/csv/tags.csv` — columns: `id, name, status` (empty `status` defaults to `ACTIVE`; tags have
no hierarchy, so no cross-reference concept applies here).

`data/question-answers/*.md` — one file per question:

```markdown
---
id: qa-some-permanent-identifier   # required — see "Identity" above
title: "Exact phrasing of the question"
categoryId: An existing category's id (exactly as written in categories.csv, e.g. cat-java)
tagIds: [An existing tag's id, Another existing tag's id]
difficulty: BEGINNER | INTERMEDIATE | ADVANCED   # optional — see below
isCommon: true | false                            # optional — see below
questionBody: "Full question text, can differ slightly from title"
shortAnswer: "2-4 sentence answer, quotable on its own"
---
<!-- detailedAnswer -->

## <Topic Name>

... full markdown body — this entire block, verbatim, becomes `detailedAnswer` ...
```

`difficulty`/`isCommon` are optional — this is general dev-knowledge Q&A, not only interview
prep. Leave them out entirely for plain "how does X work" content where forcing an
interview-difficulty/frequency judgment call wouldn't mean anything; set them only when a
question genuinely has that framing.

`categoryId`/`tagIds` reference `categories.csv`/`tags.csv` rows by their `id` column, not their
`name` — the same rationale as `parentId` above: a category/tag's `name` can be edited freely
without breaking every question that references it.

`id` values are permanent once assigned — pick something short and topic-descriptive
(`qa-react-useeffect-hook`), and never reuse it for a different question later.

An optional `slug` field can still be supplied on a question — it's a completely
separate, independent thing from `id`: the production-facing URL column, unrelated to
idempotency. Omit it (the current convention for all existing files) to derive it from `title`
via `SlugService.toSlug()` (guaranteed-aligned with production content creation, but verbose
since titles are full sentences — e.g.
`how-does-the-react-useeffect-hook-work-what-is-the-dependency-array`), or supply a short
explicit one for readability. Either way it's checked for collisions against a *different*
question and rejected rather than silently overwritten — but it plays no role in whether a
question is considered "already seeded."

Filename convention: `<something-descriptive>.md` — the loader never reads the filename itself,
only front matter, but a descriptive filename still helps anyone browsing the directory.

### Identity and slugs, in full

**`Category`/`Tag`**: `id` (→ `seedId`) is the sole idempotency key. Beyond that, a *new* `id`
whose `name` collides with an existing category/tag (created by a different `id`, or by a real
user) is rejected — `CategoryServiceImpl`/`TagServiceImpl` enforce global name uniqueness in
production (`existsByNameIgnoreCase`), and the seeder mirrors that rather than silently creating
a second same-named row. The `slug` column doesn't exist in the CSVs at all — `CategorySeeder`/
`TagSeeder` always generate it via
`SlugService.generateUniqueSlug(name, ..., ErrorCode.CATEGORY_SLUG_CONFLICT / TAG_SLUG_CONFLICT)`,
the identical call the real create-category/create-tag flow makes.

**`QuestionAnswer`**: `categoryId`/`tagIds` reference `Category`/`Tag` by their `seedId`,
resolved via `findBySeedId` — not by name or slug. `id` (→ `seedId`) is the sole idempotency key
for the question itself; if an `id` already exists but with a *different* title, that's treated
as an accidental `id` reuse (e.g. copy-pasted from another file) and rejected rather than
silently skipped — which would otherwise drop the second question's content with no warning.

## Mechanical rules (violating these breaks the seeder, not just one row)

- `id` is **required** on every question file, and on every `categories.csv`/`tags.csv`
  row. A missing `id` throws immediately.
- An `id` must never be reused for a different name/title. Reusing it (accidentally or
  otherwise) throws rather than silently adopting the new content under the old row, or silently
  creating a duplicate.
- `categoryId` **must** already exist (exact `id`) in `data/csv/categories.csv`. An unknown id
  throws and aborts the entire seeding run at startup, not just that one file.
- Every entry in `tagIds` **must** already exist (exact `id`) in `data/csv/tags.csv`. Same
  failure mode. Need a new category/tag? Add it to the CSV first (parent categories before
  children), in a separate step before writing question files that reference it.
- If `slug` is supplied explicitly on a question, it must be unique across every file.
- `difficulty`, if present, must be exactly `BEGINNER`, `INTERMEDIATE`, or `ADVANCED` — omit the
  field entirely rather than guessing when it doesn't apply.
- YAML quoting: wrap `title`/`questionBody`/`shortAnswer` in double quotes if they contain a
  colon or comma. Avoid literal `"` inside the text entirely (use backticks for code/emphasis
  instead) so you never need to escape a quote inside a quote.
- The body **must** start with `<!-- detailedAnswer -->` as its own line — this is the label
  that makes the field mapping visible when reading the raw file (front matter already labels
  every other field explicitly); the parser strips it before persisting.
- `tagIds` is a real YAML list (`[a, b, c]`), not a delimited string.

---

## Content quality criteria

Every `detailedAnswer` should satisfy all of these — not just "has code and is long":

1. **Correctness** — facts, complexity claims, and code examples are technically accurate and
   current. Don't present deprecated APIs as best practice unless discussing history explicitly.
2. **Completeness** — covers the *why* and trade-offs, not just the definition. Include edge
   cases and the "when NOT to use this" side, not just the happy path.
3. **Structure/scannability** — multiple `###` sections, at least one real code example in the
   right language, usually a comparison table. A two-paragraph summary is not full depth.
4. **`shortAnswer` vs `detailedAnswer` distinctness** — `shortAnswer` must be genuinely quotable
   on its own, not a truncated copy of the detailed answer's opening line.
5. **Practical framing, not just documentation** — this is the one most easily skipped. Include
   a `### Common mistakes` (or equivalent) section naming specific misconceptions or pitfalls —
   what separates a working understanding from a rote one. This is valuable regardless of
   whether the question has interview framing; every file should have it. Only layer on
   explicit interview-coaching language ("what an interviewer listens for") when the question
   genuinely reads as interview prep — don't force that angle onto plain knowledge content.
6. **RAG-retrieval self-containment** — see chunking constraints below; each major section
   should restate the core subject by name, not rely on the title surviving into that chunk.
7. **Corpus-level consistency** — calibrate `difficulty`/`isCommon` honestly, or leave them out
   entirely. They're optional interview-specific metadata, not required fields — don't
   pattern-match the previous file or force an even spread just to fill them in. A forced
   `ADVANCED` label (or a forced difficulty at all) on content that doesn't call for one is a
   worse error than leaving it unset. Difficulty distribution only becomes a meaningful target,
   for the subset of questions that do have interview framing, at the full-corpus scale
   (~100 questions) — not something to engineer into any single batch.

---

## RAG chunking constraints (why criterion 6 matters concretely)

Confirmed from `SimpleTextChunkingServiceImpl` (`ai-service`): chunking uses LangChain4j's
`DocumentSplitters.recursive(2048 chars, 400 chars overlap)` — a hierarchical splitter that
tries paragraph breaks → line breaks → sentences → words → raw characters, only falling to a
finer separator when needed. It is **not markdown-syntax-aware**: `##` headers, code fences, and
list markers are just text to it, with no special handling and no pre-stripping.

The text actually chunked is the whole concatenation:
`title + questionBody + "Short Answer:" + shortAnswer + "Detailed Answer:" + detailedAnswer` —
not `detailedAnswer` alone. The title appears once, at the top. Once a long `detailedAnswer`
splits into multiple ~2048-char chunks, a chunk covering a section deep in the document may
**not retain the title/topic name at all**.

Concrete rules that follow:
- Every `###` section should name the core subject explicitly in its own prose (e.g. "HashMap's
  `put`", not just "the map's insert operation") — don't assume the title survives into that
  chunk.
- Keep individual code blocks well under ~2048 characters and give them blank-line separation
  from surrounding prose — the splitter can't recognize fence boundaries, so an oversized,
  tightly-packed block risks being cut mid-fence.
- This matters more as answers get longer — be more disciplined about section length on
  `ADVANCED` content than on short `BEGINNER` answers.

---

## Planning a batch (coverage, dedup, difficulty spread)

Before generating a batch:

- Check current category/tag coverage — spread new questions across existing categories
  deliberately (rough per-category quota) rather than clustering wherever's easiest to write.
- Keep a running list of topics already covered (across *all* files, not just the current
  batch) and check new questions against it — near-duplicate topics dilute retrieval, since MMR
  diversity selection assumes the candidate pool isn't full of redundant near-copies.
- Aim for an honest mix of `difficulty` and `isCommon` across the corpus as a whole once enough
  questions exist to make that meaningful — but calibrate each individual question against
  criterion 7 above, never by quota.

If generation is parallelized across multiple agents/batches, each one needs: this guide, the
current valid category/tag id lists (or explicit permission to extend them first), and its
assigned topic slice — otherwise you get invalid category/tag references or duplicate topics
across batches that never see each other's output.

---

## After seeding: indexing is not automatic

`ContentPublishedEvent` (documented in `CLAUDE.md` as triggering async indexing on publish) is
not actually wired to anything in the current codebase — not from seeding, not from the admin
create/update endpoints either. Seeded content is **not** searchable via RAG/chat until someone
manually triggers the admin `IngestionController.indexAll()` endpoint. This is a pre-existing
gap unrelated to the seeding mechanism itself.
