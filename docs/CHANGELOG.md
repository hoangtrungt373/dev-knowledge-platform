# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

`0.0.1` is the original monolith; `0.0.2` is when the break-up into standalone microservices
began (the full six-service extraction — `ecommerce-service`, `identity-service`, `task-service`,
`social-service`, `content-service`, `ai-service` — plus `gateway`'s routing/CORS consolidation and
the reactor-wide `@ComponentScan` fix); `0.0.3` is `ecommerce-service`'s feature build-out (checkout
shipping/coupon pricing strategies, payment countdown + reconciliation) and its `gui` counterpart
work; `0.0.4` is `dev-utils-service`'s full build-out (the new module itself, its Lorem Generator/QR
Code Generator/Favorites features, its `gui` counterpart, and the code-quality/bug-fix follow-up
passes on top) that accumulated under `[Unreleased]` afterward — each cut into a real release for
the same reason: this file had grown past ~3700 lines under a single ever-growing `[Unreleased]`
section again. Full unabridged entry-by-entry history for all four lives in
[`CHANGELOG-ARCHIVE.md`](CHANGELOG-ARCHIVE.md). New entries start fresh below `[Unreleased]`.

---

## [Unreleased]

### Added

- **`dev-practice-service`: publishing a problem now requires an accepted reference submission
  (backend, Phase 1 of 3).** A problem can only reach `PUBLISHED` once an admin's `REFERENCE`
  submission has been judged `ACCEPTED` against its *current* grading contract. New
  `enums.SubmissionKind` (`USER`/`REFERENCE`); new `Problem.contractVersion` (bumped by
  `ProblemServiceImpl#update` on any signature or test-data change — the sample flag alone
  doesn't count) and `Submission.kind`/`contractVersion` (stamped by
  `SubmissionJudgeEventListener` when judging starts, so a bump makes older references stale with
  no flag to clear). New `ProblemService#isVerified`, new error code `PROBLEM_NOT_VERIFIED` (409),
  new `ProblemResponse.contractVersion`/`verified` (the latter on admin responses only). New admin
  endpoints `ProblemReferenceSubmissionApi` —
  `POST/GET /api/v1/admin/problems/{problemId}/reference-submissions[/{submissionId}]` (allowed on
  `DRAFT` problems; reference submissions never appear through `/api/v1/submissions/**`). New
  migration `DKP-0056` (`PROBLEM.CONTRACT_VERSION`, `SUBMISSION.KIND` + `CKC_SUBMISSION_KIND`,
  `SUBMISSION.CONTRACT_VERSION`, partial index `IDX_SUBMISSION_ACCEPTED_REFERENCE`). New
  `SubmissionServiceImplTest` plus six publish-verification cases in `ProblemServiceImplTest`.
  Not yet built: the admin GUI's reference-solution panel (Phase 2) and seed-file
  `referenceSolution` + publish-on-accept (Phase 3).

- **New module `dev-practice-service` — a LeetCode/NeetCode-style coding practice platform, Phase 1
  (problem catalog + submission persistence, no judging yet), own port `8088`.** Built directly
  standalone from day one, like `dev-utils-service` — never embedded in `gateway` at all, not part
  of the (closed) microservices-extraction-plan project. Unlike `dev-utils-service`, it persists its
  own schema (`dev_practice`) and verifies JWTs, since a problem catalog and per-user submission
  history are genuine stateful, ownership-scoped data.
  - `entity.Problem` — title, slug (unique, via `infra`'s `SlugService`), description, `difficulty`
    (new local `enums.Difficulty`: EASY/MEDIUM/HARD — deliberately not
    `common.enums.QuestionDifficulty`, a different domain's coincidentally-three-valued enum),
    `status` (reuses `common.enums.ContentStatus` as-is — the same DRAFT/PUBLISHED/ARCHIVED shape
    `content-service`'s `Article`/`QuestionAnswer` already use), `authorUuid` (plain column, no
    `User` FK), `publishedAt`, and a cascaded `testCases` list.
  - `entity.TestCase` — input/expectedOutput pair + `sample` flag (shown as a worked example vs.
    hidden, judge-only).
  - `entity.Submission` — `problem` FK, `userUuid` (plain column), `language`
    (`enums.ProgrammingLanguage`: JAVA/PYTHON/JAVASCRIPT), `sourceCode`, `status`
    (`enums.SubmissionStatus` — the full eventual judging vocabulary defined now; Phase 1 only ever
    produces `PENDING`).
  - `service.ProblemService`/`SubmissionService` (+ `impl/`) — `ProblemService#getPublishedBySlug`
    and `SubmissionService#create` both treat a draft/archived problem as not found, so a caller can
    never confirm the existence of unpublished content; `ProblemMapper#toPublicResponse` strips
    every hidden (`sample = false`) test case before a response ever reaches a public endpoint.
  - `api`/`api.impl` — `ProblemApi` (`/api/v1/admin/problems/**`, `ROLE_ADMIN`), `PublicProblemApi`
    (`/api/v1/public/problems/**`, unauthenticated), `SubmissionApi` (`/api/v1/submissions/**`,
    authenticated, owner-scoped).
  - `exception.DevPracticeErrorCode` — `PROBLEM_*`/`SUBMISSION_*`, implements `common`'s `ErrorCode`.
  - `config.web.WebMvcConfig` — registers `infra`'s shared `CurrentUserIdArgumentResolver` as a
    `HandlerMethodArgumentResolver` (same pattern every other standalone service's own
    `config/web/WebMvcConfig` already follows — `@Import`ing the bean alone isn't sufficient for
    Spring MVC to pick it up).
  - Own Liquibase changelog (`DKP-0052`, a fresh snapshot into the new `dev_practice` schema),
    added to the consolidated `services-liquibase` job (no standalone `*-liquibase.yml` of its own,
    same as `ecommerce-service`/`identity-service`/`content-service`/`ai-service`).
  - `gateway` wiring: `routing.GatewayRoutesConfig` gained a new `devPracticeServiceRoutes()` bean
    and `routing.GatewayServicesProperties` a matching `devPracticeServiceBaseUrl`, in both
    `application.yml` (localhost default) and `application-docker.yml` (Compose DNS name).
    `/api/v1/admin/problems/**`/`/api/v1/public/problems/**` are two more resource segments under
    the already-shared `/api/v1/admin/**`/`/api/v1/public/**` prefixes; `/api/v1/submissions/**` is
    a genuinely new top-level prefix. No `gateway`-side `SecurityConfig` change needed — its
    existing `/api/v1/admin/**`/`/api/v1/public/**` carve-outs already cover the new paths.
  - `docker-compose.apps.yml` gained a `dev-practice-service` container block, a seventh entry in
    `services-liquibase`'s migration loop, and a matching changelog volume mount. Every other
    module's own `Dockerfile` gained a `COPY dev-practice-service/pom.xml
    dev-practice-service/pom.xml` line (needed for Maven to parse the reactor's full `<modules>`
    list, same as every other sibling module's `pom.xml`).
- **`dev-practice-service` Phase 2 — LeetCode-style submission judging via a self-hosted Judge0
  instance.** Submissions are now graded, not just persisted as `PENDING` forever.
  - **Submission format changed to LeetCode-style method signatures** (a `class Solution { public
    int[] twoSum(int[] numbers, int target) { ... } }` method body), not the simpler stdin/stdout
    full-program shape considered in Phase 1 planning — a deliberate, harder choice confirmed
    directly with the user. `entity.Problem` gained `methodName`/`returnType`
    (`enums.ParamType`) and an ordered `parameters` list (new `entity.MethodParameter`, cascade
    `ALL` + `orphanRemoval`, ordered by `position`). `TestCase.input`/`expectedOutput` are now
    documented as JSON-encoded argument/return values (e.g. `[[2,7,11,15], 9]` / `[0,1]`), not raw
    text — no column type change, just a semantic one.
  - `enums.ParamType` — the closed value-shape vocabulary (`INT`/`LONG`/`DOUBLE`/`BOOLEAN`/`STRING`/
    `INT_ARRAY`/`DOUBLE_ARRAY`/`BOOLEAN_ARRAY`/`STRING_ARRAY`/`INT_MATRIX`) every signature and
    every harness is restricted to.
  - `harness.LanguageHarness` (abstract, **Template Method**: `buildProgram` is the fixed
    prelude → user-code → generated-`main` skeleton) + one concrete subclass per
    `ProgrammingLanguage` (`JavaLanguageHarness`, `PythonLanguageHarness`,
    `JavaScriptLanguageHarness`), selected by `harness.LanguageHarnessRegistry` (the **Strategy**
    half of the same design). `JavaLanguageHarness` embeds a hand-rolled `JsonMini` parser/writer
    verbatim into every generated Java program, since Judge0's Java runtime has no application
    classpath (no Jackson) — Python's `json`/JavaScript's native `JSON` need no equivalent.
    **Verified against the real JDK 21 compiler and a real Node runtime in-session**: the actual
    generated `JsonMini` class, a full generated two-sum `Main.java`, and the generated JS harness
    were extracted and compiled/run for real (not just read-through), including a passing two-sum
    test and a quote/backslash JSON escaping round-trip test. The Python harness was not executed
    (its `json`/`typing`-stdlib logic carries much lower risk).
  - `judge.JudgeClient` (**Adapter**, Structural pattern) + `judge.impl.Judge0Client`
    (`RestClient`-backed, `base64_encoded=true` submissions, polls `GET /submissions/{token}` per
    new `config.JudgeClientProperties` — `app.judge0.*`, interval/attempt-bounded) +
    `judge.Judge0Status`/`Judge0SubmissionResult`. Deliberately never sends Judge0's own
    `expected_output` field — pass/fail is this module's own structural JSON comparison
    (`SubmissionJudgeEventListener#matches`, via Jackson's `JsonNode` equality), not Judge0's raw
    string compare, so formatting-only differences like `[0, 1]` vs `[0,1]` don't cause a false
    `WRONG_ANSWER`.
  - `event.SubmissionCreatedEvent` (published by `SubmissionServiceImpl.create`) +
    `event.SubmissionJudgeEventListener` — judges every `TestCase` in order, stopping at the first
    failure. **Uses `@TransactionalEventListener(phase = AFTER_COMMIT)` + explicit
    `@Async("asyncEventExecutor")`, not this reactor's usual `@EventHandler` composed annotation** —
    a real race was caught and fixed during this build: `@EventHandler` fires the instant
    `publishEvent` is called, which could be before the publishing transaction commits, so the
    listener's own separate transaction could fail to see the just-created row under default
    isolation. `AFTER_COMMIT` removes that race entirely. Splits its own work across two short
    `TransactionTemplate` transactions (load-and-mark-`RUNNING`, save-final-outcome) around a long,
    deliberately non-transactional middle (the Judge0 round-trips), rather than holding one open
    transaction (and a DB connection/locks) for the whole judging run.
  - `Submission` gained `passedTestCases`/`totalTestCases`/`errorMessage` (all nullable — unset
    until judging finishes), surfaced on `SubmissionResponse`.
  - New public endpoint `GET /api/v1/public/problems/{slug}/starter-code?language=...` —
    `harness.LanguageHarness#renderStarterCode`'s method-signature stub, for a code editor to
    pre-fill before the user has written anything.
  - New Liquibase changeset `DKP-0053` (additive-only, per this reactor's
    never-edit-an-already-run-changeset convention — `DKP-0052` stays untouched): `PROBLEM` gained
    `METHOD_NAME`/`RETURN_TYPE` (backfilled via a placeholder default then the default dropped,
    since Phase 1 had no method-signature concept at all), new table `METHOD_PARAMETER`
    (`ON DELETE CASCADE` from `PROBLEM`), `SUBMISSION` gained its three new nullable columns.
  - `DevPracticeServiceApplication` gained `@EnableAsync` + `@Import(AsyncEventThreadPoolConfig.class)`
    + `@EnableConfigurationProperties({AsyncEventThreadPoolProperties.class,
    JudgeClientProperties.class})` — this module's first real `AsyncEventHandler` subclass, Phase 1
    had dispatched no events at all.
  - **Self-hosted Judge0 added to `docker-compose.infra.yml`** — `judge0-db`/`judge0-redis`/
    `judge0-server`/`judge0-workers` (own Postgres/Redis, deliberately separate from this reactor's
    own `postgres`/`redis` services), config in new `docker/judge0/judge0.conf`. Authored from
    Judge0's own documented compose/config template, unverified at runtime, same caveat every other
    standalone service's first-landing infra carries in this reactor.
  - **Follow-up, same day: switched the default Judge0 backend to Judge0 CE's hosted RapidAPI
    instance, keeping the self-hosted stack above as a documented fallback rather than the active
    default.** Reason: Judge0's `isolate` sandbox needs real cgroup/namespace control
    (`privileged: true`), which has known friction on Docker Desktop/WSL2 (Windows) — switching
    avoids fighting that while this judging pipeline is still being verified end-to-end, without
    losing the self-hosted option. `config.JudgeClientProperties` gained `rapidApiKey`/
    `rapidApiHost`; `judge.impl.Judge0Client` now adds `X-RapidAPI-Key`/`X-RapidAPI-Host` request
    headers only when `rapidApiKey` is set, so the same class works unmodified against either
    backend. `application.yml`'s `app.judge0.base-url` default changed to
    `https://judge0-ce.p.rapidapi.com`; `docker-compose.apps.yml`'s `dev-practice-service` container
    now reads `JUDGE0_RAPIDAPI_KEY` from the host shell (same pattern as `OPENAI_API_KEY`) instead
    of hardcoding `JUDGE0_BASE_URL` at the local `judge0-server` — repoint `JUDGE0_BASE_URL` at
    `http://judge0-server:2358` and leave `JUDGE0_RAPIDAPI_KEY` unset to switch back. Neither
    backend has actually been exercised against this module's judging pipeline in this session yet.
  - **Follow-up, same day: removed the self-hosted `judge0-*` stack outright**, on request, once
    RapidAPI became the only backend actually in use — no point carrying ~50 lines of never-run
    compose config (`judge0-db`/`judge0-redis`/`judge0-server`/`judge0-workers` in
    `docker-compose.infra.yml`, plus `docker/judge0/judge0.conf`) for a path nothing exercises.
    `judge.impl.Judge0Client`/`config.JudgeClientProperties` are unaffected — they still support a
    self-hosted instance unmodified (leave `rapidApiKey` blank, repoint `baseUrl`), so reintroducing
    one later is a compose/config addition, not a code change.
  - **Follow-up, same day: locked a `PUBLISHED` problem's method signature, but deliberately left
    `testCases` always editable — closing a real correctness gap spotted by the user while reviewing
    `Submission`'s new columns.** `ProblemServiceImpl#update` now rejects (`PROBLEM_SIGNATURE_LOCKED`,
    `409`) any change to `methodName`/`returnType`/`parameters` while a problem is (and stays)
    `PUBLISHED` — comparing the incoming signature against what's currently persisted
    (`signatureChanged`) before mutating anything. Escape hatch: move the problem to
    `DRAFT`/`ARCHIVED` in the same update request (the check only fires when the problem stays
    `PUBLISHED` through the call). `testCases` are intentionally **not** part of this lock — a
    test-case edit never invalidates already-submitted `sourceCode`, only what counts as correct
    going forward, so there's no correctness reason to require unpublishing first (real judges add
    test cases to live problems routinely). What's enforced instead, unconditionally, on every
    create/update: `ProblemServiceImpl#validateTestCaseArity` — every `TestCase.input` must parse as
    a JSON array whose length matches the final `parameters` list's size
    (`PROBLEM_TEST_CASE_ARITY_MISMATCH`, `400`, via a new `ObjectMapper` dependency injected into
    `ProblemServiceImpl`) — this is what actually protects a test case from silently drifting out of
    sync with a (possibly frozen) signature now that the two aren't locked together. New
    `DevPracticeErrorCode.PROBLEM_003`/`PROBLEM_004`. Accepted, explicitly-not-solved trade-off:
    editing/removing a `TestCase` never retroactively re-judges any `Submission` already graded
    against the old set (`passedTestCases`/`totalTestCases`/`status` are a one-time snapshot from
    whenever `SubmissionJudgeEventListener` ran) — same behavior every real competitive-judge
    platform has.
  - **Follow-up, same day: moved Judge0's `language_id` mapping off `ProgrammingLanguage` and into
    configuration — a vendor-coupling smell spotted by the user while reviewing the enum.**
    `ProgrammingLanguage` previously carried its own hardcoded `judge0LanguageId` field
    (`JAVA(62), PYTHON(71), JAVASCRIPT(63)`); a Judge0-specific implementation detail leaking into a
    domain enum used well beyond the judge subsystem (`Submission.language`, DTOs,
    `LanguageHarnessRegistry`), and something a future different-judge-backend swap would have had
    to touch, defeating the point of `JudgeClient` being an Adapter. Reverted to a plain
    `JAVA`/`PYTHON`/`JAVASCRIPT` enum with zero vendor detail — the same discipline `Judge0Status`
    already applies in the other direction for submission statuses. The mapping moved to
    `config.JudgeClientProperties#getLanguageIds()` (`app.judge0.language-ids.*`, a
    `Map<ProgrammingLanguage, Integer>`, bound from `application.yml`'s
    `language-ids: {java: ..., python: ..., javascript: ...}` with per-entry env-var overrides
    `JUDGE0_LANGUAGE_ID_JAVA`/`_PYTHON`/`_JAVASCRIPT`) — since these ids are already known to be
    per-deployment and unverified, a wrong one is now fixable with an env var, not a code change and
    redeploy. `judge.impl.Judge0Client`'s constructor fails fast at startup
    (`IllegalStateException`) if the configured map is missing an entry for any
    `ProgrammingLanguage` constant, rather than letting a missing id surface later as a confusing
    per-submission failure.
  - Not built this phase (deliberately deferred, not rejected): the webhook-callback alternative to
    polling, admin GUI/seed data for authoring problems, and any GUI code-editor/submission flow.
    See `dev-practice-service/CLAUDE.md`'s "Phase 2" section for the full list.

- **`dev-practice-service`'s first tests.** `harness.LanguageHarnessGoldenTest` (snapshot: every
  fixture's starter code + full generated program per language, byte-compared to checked-in
  `src/test/resources/harness/golden/**` files; `-Dharness.golden.update=true` regenerates them),
  `harness.LanguageHarnessExecutionIT` (Testcontainers — compiles/runs every generated program in
  Judge0 CE's own runtime versions, `openjdk:13-jdk-slim`/`python:3.8-slim`/`node:12-slim`, including
  an `identity(T) -> T` round-trip for every `ParamType` in every language; skipped without Docker,
  and excluded from plain `mvn test` by its `IT` suffix), and `judge.OutputMatcherTest`. New test
  dependency `org.testcontainers:junit-jupiter`. The goldens were captured from the pre-refactor
  code first, so the refactor below is proven byte-identical rather than assumed.
- `judge.OutputMatcher` — the submission-vs-expected-output comparison, extracted from
  `SubmissionJudgeEventListener`'s private `matches` into its own `@Component` so it's unit-testable
  and the execution IT can judge with the exact production logic.

- **`dev-practice-service`: judge-side failure handling.** New `SubmissionStatus.JUDGE_ERROR`
  (changeset `DKP-0054` widens `CKC_SUBMISSION_STATUS`), new `judge.JudgeUnavailableException`
  (part of `JudgeClient`'s contract — Spring's `RestClientException`s no longer cross the Adapter),
  new `config.Judge0RestClientConfig` (builds the Judge0 `RestClient`), new `app.judge0.*`
  properties `connect-timeout`/`read-timeout`/`retry.{max-attempts,initial-interval,multiplier,
  max-interval}` (with `JUDGE0_*` env overrides), new dependency `org.springframework.retry:spring-retry`,
  and new tests `judge.impl.Judge0ClientTest` (`MockRestServiceServer` as a scripted fake Judge0) and
  `event.SubmissionJudgeEventListenerTest`.

- **`infra`: shared polling component `polling.PollingTemplate`, built on Resilience4j Retry.** A
  blocking "call, check, wait, repeat" loop (template-callback style, like `JdbcTemplate`): the
  caller supplies a probe and a completion predicate, the template owns the attempt budget, fixed
  wait and interrupt handling, using Resilience4j's result-based retry (`retryOnResult`) with
  exceptions deliberately not retried. Supporting types: `PollingPolicy` (record: name, maxAttempts,
  interval; one name must always mean one policy, and a conflicting re-registration fails fast),
  `PollOutcome` (sealed: `Completed`/`TimedOut` — running out of attempts is a normal outcome, not
  an exception), `PollingInterruptedException`. Works around a real Resilience4j 2.1.0 quirk: an
  interrupted result-based wait surfaces as a `NullPointerException` with the interrupt flag lost;
  the template translates it and restores the flag. New dependency
  `io.github.resilience4j:resilience4j-retry` on `infra` (version from `spring-cloud-dependencies`,
  2.1.0). New test `polling.PollingTemplateTest` (`infra`'s first test class). Registered only via explicit
  `@Import(PollingTemplate.class)` — `dev-practice-service` is its first consumer.

- **`gui`: new `@dev-practice` feature — admin screens for `dev-practice-service`'s problem
  catalog** (`/admin/problems`, `/admin/problems/new`, `/admin/problems/:id/edit`, under the
  `ADMIN`-guarded `AdminLayout`; new "Dev Practice → Problems" sidebar group and dashboard card).
  `pages/ProblemListPage` (search, difficulty/status filters, status/difficulty chips, delete via
  `ConfirmDialog`, `TablePagination`) and `pages/ProblemFormPage` (one page for create/edit: title,
  Markdown description, method signature, test cases, difficulty/status sidebar).
  `components/MethodSignatureEditor` (method name, return type, ordered parameters with up/down
  reorder, a live signature preview, type picker showing a JSON example per `ParamType`),
  `components/TestCaseEditor` (inline card per test case, "N of M arguments" hint, sample switch),
  `components/JsonCodeField` (small CodeMirror JSON field). `utils/problemForm.ts` mirrors the
  backend's rules client-side — test-case arity (`PROBLEM_TEST_CASE_ARITY_MISMATCH`) and the
  published-signature lock (`PROBLEM_SIGNATURE_LOCKED`, with a Revert action) — plus two the
  backend doesn't enforce: method/parameter names must be valid identifiers, parameter names must
  be unique. New `@dev-practice/*` path alias (`tsconfig.json` + `vite.config.ts`).
- **`dev-practice-service`: new `dto.AdminProblemSummaryResponse`** (id, slug, title, difficulty,
  status, publishedAt, createdAt) + `ProblemMapper#toAdminSummaryResponse`.
- **`gui`: shared form "Tags" section — `@shared/components/TagPicker` +
  `@shared/hooks/useStagedTagPicker`.** The tag picker logic moved out of
  `@ecommerce/hooks/useProductTags` into a generic hook (tag type generic; `loadTags`/`createTag`
  injected by the caller), and the section markup ("Existing tags" chip cloud + "New tags" queue,
  created only on save) out of `ProductFormPage` into a presentational `TagPicker`.
  `useProductTags` is now a thin wrapper (same result shape, so `ProductFormPage`'s submit logic is
  unchanged; its section is now `<TagPicker>`, ~77 lines removed). `ProblemFormPage`'s Tags panel
  uses the same pair, gaining the "New tags" queue: queued names are created right before the
  problem is saved, after validation passes.
- **`dev-practice-service`: `scripts/purge-seed-data.sql`** — dev utility mirroring
  `ecommerce-service`'s: `TRUNCATE ... RESTART IDENTITY CASCADE` of all 6 `dev_practice` tables
  (`PROBLEM`, `TEST_CASE`, `METHOD_PARAMETER`, `PROBLEM_TAG`, `PROBLEM_TAG_ASSIGNMENT`,
  `SUBMISSION`) so the name/slug-idempotent seeders reseed edited seed files. Wipes real submission
  history too; local/dev databases only.
- **`dev-practice-service`: problem seeding + the first seeded problem, Evaluate Reverse Polish
  Notation.** New `service.seed.ProblemSeeder` (implements `infra`'s `Seeder`) and
  `DevPracticeDataSeedingRunner` (gated by new `app.seed.enabled` / `APP_SEED_ENABLED`, default
  `false`; `true` in `application-local.yml`), reading `resources/data/problems/*.md` — the same
  front-matter-plus-Markdown file shape as `content-service`'s Q&A seeds. The signature is parsed
  from the file's Java code template by `SignatureTemplateParserRegistry`, and the problem is
  created through `ProblemService.create`, so seed data passes every normal validation; idempotent
  by slug. New `ProblemTagRepository#findByNameIgnoreCase`. **Plus `ProblemTagSeeder`** (extends
  `infra`'s `CsvSeeder`, same shape as `ecommerce-service`'s `ProductTagSeeder`): the 18 starter
  topics from new `data/csv/problem_tags.csv`, idempotent by name, created through
  `ProblemTagService.create`; the runner calls it before `ProblemSeeder` (which resolves tags by
  name). Replaces the `INSERT` that had been in `DKP-0055` — one source for the starter list, and
  editing it no longer needs a migration. New test `ProblemTagSeederTest`; `ProblemSeederTest` now
  only resolves tag names present in that CSV. New seed file
  `evaluate-reverse-polish-notation.md` (MEDIUM; Array/Math/Stack; `int evalRPN(String[] tokens)`;
  18 test cases, 2 shown as samples, every expected output computed by a reference
  implementation). New test `ProblemSeederTest` (runs every seeded test case through a reference
  solution).
- **Code-template signature import — paste a LeetCode-style template, get the method signature
  (`dev-practice-service` + `gui`).** E.g. `class Solution { public int evalRPN(String[] tokens) {} }`
  → `evalRPN`, `INT`, `[tokens: STRING_ARRAY]`.
  - **Backend:** new `POST /api/v1/admin/problems/parse-template` (`{language, code}` →
    `ParsedSignatureResponse`; nothing persisted — the structured signature stays the source of
    truth, and starter code for every language is still generated from it). New `harness`
    Strategy `SignatureTemplateParser` with `JavaSignatureTemplateParser` (real AST via new
    dependency `com.github.javaparser:javaparser-core:3.28.2`), `PythonSignatureTemplateParser`
    (type hints required) and `JavaScriptSignatureTemplateParser` (JSDoc `@param`/`@return`
    required), selected by `SignatureTemplateParserRegistry` (fails startup if a language lacks
    one). Types resolve through `TypeDeclarationIndex`, which inverts each language's own
    `TypeRenderer` — so parsing and starter rendering can't disagree, and a spelling that's
    ambiguous in that language (Python `int` = INT/LONG; JS `number` = INT/LONG/DOUBLE, `number[]`
    = INT_ARRAY/DOUBLE_ARRAY) returns the most common type plus `alternatives`. New error code
    `PROBLEM_TEMPLATE_INVALID` (`PROBLEM_008`) with a fix-it message (no method, several methods,
    method outside `Solution`, missing hint/JSDoc, unsupported type + the supported list). New test
    `SignatureTemplateParserTest`, including a round trip of every harness's own starter code.
  - **`gui`:** new `components/CodeTemplateImporter` above the signature editor in
    `ProblemFormPage` (Java/Python/JavaScript toggle, CodeMirror editor, Parse button, inline error;
    disabled while the signature is locked); parsing fills method name/return type/parameters, and
    `MethodSignatureEditor` shows a warning under any type that was a guess until the admin changes
    it. New dependencies `@codemirror/lang-java`, `@codemirror/lang-python`.
- **Problem tags (topics such as Array, Math, Stack) — `dev-practice-service` + `gateway` + `gui`.**
  Mirrors `ecommerce-service`'s Product Tags.
  - **Schema (`DKP-0055`):** `PROBLEM_TAG` (name, slug; case-insensitive name uniqueness via a
    `LOWER(NAME)` unique index) and an explicit `PROBLEM_TAG_ASSIGNMENT` join entity (audit
    columns; cascades from `PROBLEM`, deliberately not from `PROBLEM_TAG`). Schema only — the
    starter topics are seeded by `ProblemTagSeeder` (see the problem-seeding entry; an earlier
    draft of `DKP-0055` inserted them itself, removed before the changeset ever ran anywhere).
  - **Backend:** entities `ProblemTag`/`ProblemTagAssignment` (`Problem.tagAssignments`),
    `ProblemTagRepository`/`ProblemTagAssignmentRepository`, `ProblemTagService` (+ impl),
    `ProblemTagApi` (`/api/v1/admin/problem-tags`: CRUD, paged list, `GET /all`) and
    `PublicProblemTagApi` (`/api/v1/public/problem-tags`). Problem create/update take optional
    `tagIds` (update: `null` = unchanged, `[]` = clear); applied by diffing rather than
    clear-and-rebuild, since Hibernate flushes INSERTs before orphan DELETEs and re-adding an
    existing tag would violate the pair's unique constraint. Problem responses (detail + both list
    rows) embed `tags: [{id, name, slug}]`. Admin and public problem lists take a repeated
    `tagIds` filter (ANY-match, an `EXISTS` subquery). New error codes `PROBLEM_TAG_NOT_FOUND`,
    `PROBLEM_TAG_NAME_CONFLICT`, `PROBLEM_TAG_SLUG_CONFLICT`, `PROBLEM_TAG_IN_USE` (delete refused
    while assigned). New tests `ProblemTagServiceImplTest` + tag cases in `ProblemServiceImplTest`.
  - **`gateway`:** `devPracticeServiceRoutes()` gained `/api/v1/admin/problem-tags/**` and
    `/api/v1/public/problem-tags/**`.
  - **`gui`:** new `/admin/problem-tags` page (`ProblemTagListPage` + `ProblemTagFormDialog`,
    "Dev Practice → Problem Tags" in the sidebar); a chip tag picker in `ProblemFormPage`'s sidebar
    (always sends the full `tagIds` set); a Tags column (first 3 chips + "+N") and a multi-select
    tag filter on `ProblemListPage`.
- **`dev-practice-service`: signature-name validation — `harness.SignatureNameValidator`.** A
  problem's method and parameter names must (1) match `^[A-Za-z][A-Za-z0-9_]*$` (also enforced
  field-level via `@Pattern` on `CreateProblemRequest`/`UpdateProblemRequest`/
  `MethodParameterRequest`; a leading `_` is reserved for the harnesses' own locals), (2) not be
  reserved by *any* language — new `resources/harness/{java,python,javascript}/reserved-names.txt`,
  each language's keywords plus names its generated code depends on (Python `self`; JavaScript
  `require`/`JSON`/`console`, which a `var <methodName>` declaration would otherwise shadow),
  loaded by `LanguageHarness` at construction (new `reservedNames()`), and (3) be unique. New
  error codes `PROBLEM_INVALID_IDENTIFIER` (`PROBLEM_005`), `PROBLEM_DUPLICATE_PARAMETER_NAME`
  (`PROBLEM_006`). New tests `SignatureNameValidatorTest`, `ProblemServiceImplTest`.
- **`dev-practice-service`: deleting a problem with submissions is refused** —
  `ProblemServiceImpl#delete` checks new `SubmissionRepository#countByProblem_Id` first and throws
  new `PROBLEM_HAS_SUBMISSIONS` (`PROBLEM_007`, 409, "archive it instead"). Deliberately no
  `ON DELETE CASCADE` migration: submissions are users' own history.

### Changed

- **`dev-practice-service`: a published problem's test cases are no longer freely editable.**
  Changing the signature *or* the test data of a `PUBLISHED` problem is now refused with
  `PROBLEM_NOT_VERIFIED` — save it as `DRAFT`, run a reference solution, then publish again. Creating
  a problem directly as `PUBLISHED` is refused too. Problems already published before `DKP-0056` stay
  live until their contract is next edited. Until the Phase 2 GUI panel exists, the admin form's
  Publish action returns 409 for any problem without a reference. Deleting a problem now ignores
  (and removes) its `REFERENCE` submissions; only `USER` submissions block a delete. The seeded
  Evaluate Reverse Polish Notation problem is now seeded as `DRAFT`.

- **`dev-practice-service`: `GET /api/v1/admin/problems` now returns `AdminProblemSummaryResponse`
  rows instead of `ProblemSummaryResponse`** — the admin list needed `status`; the public list
  (`/api/v1/public/problems`) keeps the lean `ProblemSummaryResponse` unchanged.
- **`gui`: `MarkdownField` moved from `@content/components/` to `@shared/components/`** — its
  second consumer (`@dev-practice`'s problem description) arrived; same promotion precedent as
  `TableStatusRow`. `QuestionAnswerFormPage`'s import updated; component unchanged.

- **`dev-practice-service`: `Judge0Client` moved onto `infra`'s `PollingTemplate` and Resilience4j,
  replacing its hand-written poll loop and Spring Retry.** Status polling now goes through
  `PollingTemplate` (policy `judge0-submission-status`, from the unchanged
  `app.judge0.poll-interval-ms`/`max-poll-attempts`); per-call transient-failure retry is a
  Resilience4j `Retry` (`judge0-call`, same `app.judge0.retry.*` settings, same retried failure
  set). The two policies are nested, not merged: the poll policy never retries exceptions, the call
  policy never retries results. Behavior is unchanged except that no wait happens after the final
  poll attempt anymore. Dependency `org.springframework.retry:spring-retry` removed from
  `dev-practice-service`, replaced by `io.github.resilience4j:resilience4j-retry`.
  `Judge0Client`'s constructor takes a `PollingTemplate`; `DevPracticeServiceApplication` now
  imports it.

- **`dev-practice-service` harness refactor: program skeletons moved to JMustache templates, per-type
  syntax moved to `TypeRenderer` strategies.** `LanguageHarness`'s abstract
  `renderPrelude`/`renderMain`/`renderStarterCode` steps are gone; each language's
  prelude/main/starter now lives in `src/main/resources/harness/{java,python,javascript}/*.mustache`,
  and Java's embedded `JsonMini` helper is a plain `harness/java/JsonMini.java` resource instead of a
  double-escaped text block. The per-`ParamType` switch statements previously scattered through each
  harness (`javaType`/`toMethod`/`typeHint`/`jsType`) are consolidated into one exhaustive switch
  per language (`JavaTypeRenderer`/`PythonTypeRenderer`/`JavaScriptTypeRenderer`, returning a
  `TypeSyntax` record), keeping the compile-time "every harness supports every `ParamType`" check.
  The three harness subclasses are now constructor-only. `language()` is now `final` on the base
  class. New dependency `com.samskivert:jmustache` (used directly, not via
  `spring-boot-starter-mustache`, to avoid an unneeded HTML view resolver). Root `.gitattributes`
  pins the harness resources and golden files to `eol=lf`.

### Fixed

- **Keycloak crash-looped on a fresh database: `docker/keycloak/realm-export.json` used
  `hideOnLoginPage`**, a field name Keycloak 26 (`quay.io/keycloak/keycloak:26.0`) no longer knows
  — its importer fails on unknown fields (`Unrecognized field "hideOnLoginPage"`). Renamed to
  `hideOnLogin`. Went unnoticed because `--import-realm` skips an already-existing realm; it only
  ran (and failed) once the `keycloak` schema was empty. `docker/keycloak/README.md` updated.

- **`dev-practice-service`: `PROBLEM_TEST_CASE_ARITY_MISMATCH`'s API message was the raw test-case
  input JSON**, not an explanation — `Validator.isTrue(..., testCase.input())` passed a lone
  `String`, which binds to the `isTrue(..., String message)` overload and replaces the error code's
  template. Now passes the input *and* the expected count, against a new template ("Test case input
  {0} must be a JSON array with exactly {1} value(s), one per parameter").

- **`dev-practice-service`: a correct `DOUBLE`/`DOUBLE_ARRAY` answer could be judged
  `WRONG_ANSWER`.** Comparison was Jackson `JsonNode.equals`, which is numeric-type-sensitive:
  JavaScript's `JSON.stringify(2.0)` prints `2` where Java/Python print `2.0`, so no single
  `expectedOutput` could pass in all three languages; floating-point rounding (`0.1 + 0.2`) also
  failed outright. `OutputMatcher` now compares floating-point return types within `1e-5` (absolute,
  or relative above 1 — LeetCode's own convention) and every other numeric type by exact
  `BigDecimal` value (so `2` and `2.0` now also match for an integral return type).
- **`dev-practice-service`: a Java `long` argument beyond 2^53 was silently rounded.** `JsonMini`
  parsed every JSON number as a `Double` before `toLong` narrowed it; integral literals now parse as
  `Long`. (JavaScript's own `JSON.parse` has the same limit natively — unchanged, same as LeetCode's
  JS judge.)
- **`dev-practice-service`: a Java `String` answer containing a newline (or any control character)
  was judged `WRONG_ANSWER`.** `JsonMini.write(String)` escaped only `"`/`\`, so control characters
  were printed raw — invalid JSON. It now escapes `\n \r \t \b \f`, every other control character,
  and all non-ASCII as `\uXXXX` (pure-ASCII output, independent of the sandbox JVM's default
  charset). `JsonMini.parseString` gained the matching `\r \b \f \uXXXX` decoding (it previously
  turned `é` into the literal text `u00e9`), and the generated `Main` now reads stdin as
  explicit UTF-8 — Judge0's JDK 13 predates JDK 18's UTF-8-by-default, so a raw non-ASCII test
  input depended on the sandbox locale. `LanguageHarnessExecutionIT`'s `STRING` sample now covers
  all of these.
- **`dev-practice-service`: calls to Judge0 had no timeouts.** A hung connection could block a
  judging thread indefinitely — the poll ceiling only counts completed polls. Connect/read timeouts
  are now configured on the Judge0 `RestClient` (defaults 5s/15s).
- **`dev-practice-service`: any Judge0 HTTP error left the submission stuck in `RUNNING` forever.**
  The exception escaped to `AsyncEventHandler#handle`, which only logs it, so the final status was
  never saved. Transient failures (429 — likely on RapidAPI's free tier — 502/503/504, I/O errors)
  are now retried with configurable exponential backoff, and whatever still fails — or any
  unexpected bug during judging — ends as `JUDGE_ERROR` with a generic, user-safe message
  (operator detail is logged only).
- **`dev-practice-service`: Judge0 response decoding used the strict base64 decoder.** Judge0 (Ruby
  `Base64.encode64`) is expected to line-wrap its base64 output, which `Base64.getDecoder()`
  rejects; responses now use `Base64.getMimeDecoder()`. Not yet confirmed against a live Judge0
  response — the MIME decoder is correct either way.

- Reactor version bumped `0.0.3-SNAPSHOT` → `0.0.4-SNAPSHOT` (root `pom.xml`'s `<revision>`), and
  `docs/CHANGELOG.md`'s `[Unreleased]` section (everything accumulated since the `0.0.3` cut —
  entirely `dev-utils-service`'s build-out) was cut into a real `[0.0.4]` release and archived to
  `docs/CHANGELOG-ARCHIVE.md`, following the exact same pattern as the `0.0.2`/`0.0.3` cuts. See
  root `CLAUDE.md`'s Documentation Protocol section note for the updated version history.

## [0.0.4] — 2026-09-17

Retroactive cut of everything that had accumulated under `[Unreleased]` since the `0.0.3` cut —
entirely `dev-utils-service`'s build-out: the new module itself (JSON format/validate, YAML↔JSON
conversion, HTML beautify), its Lorem Generator, QR Code Generator, and Favorites features, the
`gui` counterpart work for all of it, and the several code-quality analysis passes and bug-fix
follow-ups that accumulated on top — cut into a real release for the same reason `0.0.2`/`0.0.3`
were: this file had grown past ~3700 lines under a single ever-growing `[Unreleased]` section
again. See [`CHANGELOG-ARCHIVE.md`](CHANGELOG-ARCHIVE.md) for the complete, unabridged
entry-by-entry history.

---

## [0.0.3] — 2026-09-07

Retroactive cut of everything that had accumulated under `[Unreleased]` since the `0.0.2` cut —
almost entirely `ecommerce-service`'s feature build-out (shipping/coupon pricing strategies,
payment countdown + reconciliation job) and its `gui` counterpart work. See
[`CHANGELOG-ARCHIVE.md`](CHANGELOG-ARCHIVE.md) for the complete, unabridged entry-by-entry history.

---

## [0.0.2] — 2026-08-11

Retroactive cut of everything that had accumulated under `[Unreleased]` up to this point — the
start of the microservices break-up. See [`CHANGELOG-ARCHIVE.md`](CHANGELOG-ARCHIVE.md) for the
complete, unabridged entry-by-entry history.

---

## [0.0.1] — Initial release

The original monolith. See [`CHANGELOG-ARCHIVE.md`](CHANGELOG-ARCHIVE.md) for the full entry.
