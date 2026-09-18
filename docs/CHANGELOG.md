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

### Changed

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
