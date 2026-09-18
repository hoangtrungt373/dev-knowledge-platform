# CLAUDE.md — dev-practice-service

Module-local guidance for `dev-practice-service`. Read alongside the root `CLAUDE.md`.

## What lives here

A LeetCode/NeetCode-style coding practice platform: a problem catalog (Phase 1) plus LeetCode-style
method-signature submission judging via a self-hosted Judge0 instance (Phase 2, both now built).
Package root: `com.ttg.devknowledgeplatform.devpractice.*`.

**Built directly as a standalone Spring Boot application from day one** — like `dev-utils-service`,
never embedded in `gateway` at all, so it is not part of the (closed) microservices-extraction-plan
project (see root `CLAUDE.md`'s Long-term direction section). Its own `DevPracticeServiceApplication`
entry point, its own `dev_practice` Postgres schema (same `dev-premier` database, not a separate
instance — per-service-per-schema, see root `CLAUDE.md`'s Database Conventions), its own port
(`8088`), and its own Liquibase changelog (`DKP-0052` — Phase 1's fresh-snapshot `PROBLEM`/
`TEST_CASE`/`SUBMISSION` tables; `DKP-0053` — Phase 2's additive `METHOD_NAME`/`RETURN_TYPE`
columns, the new `METHOD_PARAMETER` table, and `SUBMISSION`'s judging-result columns, per this
repo's never-edit-an-already-run-changeset convention). Routed through `gateway`'s
`routing/GatewayRoutesConfig` (`devPracticeServiceRoutes()`) — `/api/v1/admin/problems/**` and
`/api/v1/public/problems/**` (including `/starter-code`) are two more resource segments under the
already-shared `/api/v1/admin/**`/`/api/v1/public/**` prefixes; `/api/v1/submissions/**` is a
genuinely new top-level prefix.

**Submissions are LeetCode-style method bodies, not full stdin/stdout programs** — a deliberate
choice over the simpler competitive-programming-judge shape (see the
`project_dev_practice_service_module` memory for the full discussion of that trade-off). This is
why `Problem` carries a `methodName`/`returnType`/ordered `parameters` signature, and why
`TestCase.input`/`expectedOutput` are JSON-encoded argument/return values, not raw text.

- `DevPracticeServiceApplication` — `@SpringBootApplication` + `@EnableAsync` +
  `@Import({JacksonConfig.class, TraceContextFilter.class, SlugServiceImpl.class,
  KeycloakRealmRoleConverter.class, KeycloakJwtAuthenticationConverter.class,
  CurrentUserIdArgumentResolver.class, GlobalExceptionHandler.class,
  AsyncEventThreadPoolConfig.class})` +
  `@EnableConfigurationProperties({AsyncEventThreadPoolProperties.class,
  JudgeClientProperties.class})` entry point — names the exact `infra` beans this module uses
  instead of a broad `@ComponentScan`/`@ConfigurationPropertiesScan` into the sibling `infra`
  package (see root `CLAUDE.md`'s "Post-extraction hardening" section for the three-round bug
  history that established this convention). `SlugServiceImpl` is imported for `Problem` slug
  generation, the same mechanism `content-service` uses for `Category`/`Tag` slugs.
  `AsyncEventThreadPoolConfig`/`@EnableAsync` were added in Phase 2 once
  `event.SubmissionJudgeEventListener` became this module's first real `AsyncEventHandler`
  subclass — Phase 1 had neither, since it dispatched no events at all.
  `JudgeClientProperties` (`app.judge0.*`) is this module's own local `@ConfigurationProperties`
  class. **No `@EntityScan`/`@EnableJpaRepositories`** — this module doesn't touch
  `common.entity.User`/`common.repository.UserRepository` at all (see "No local `User` copy" below).
- `security/SecurityConfig` — this app's own filter chain, independent of `gateway`'s (mirrors
  `content-service`'s three-way split): `/api/v1/public/**` (published-problem browsing, incl.
  starter-code) permits all, `/api/v1/admin/**` (problem-catalog CRUD) requires `ROLE_ADMIN`,
  everything else (`/api/v1/submissions/**`) requires authentication only — ownership, not role,
  scopes a caller to their own submissions.
- `config/web/WebMvcConfig` — registers `infra.security.CurrentUserIdArgumentResolver` as a
  `HandlerMethodArgumentResolver` so `@CurrentUserId String`-annotated controller parameters
  actually resolve; `@Import`ing the bean alone (in `DevPracticeServiceApplication`) isn't enough —
  Spring MVC only picks up argument resolvers registered through a `WebMvcConfigurer`, same as
  every other standalone service's own `config/web/WebMvcConfig`.
- `entity/` — `Problem`, `MethodParameter` (`@ManyToOne` onto `Problem`, cascade `ALL` +
  `orphanRemoval`, ordered by `position` — a signature's parameter order is semantically
  load-bearing), `TestCase` (same cascade shape, ordered by `id`), `Submission`. `Problem` reuses
  `common.enums.ContentStatus` (DRAFT/PUBLISHED/ARCHIVED) for its publish lifecycle — the exact
  three-state shape `content-service`'s `Article`/`QuestionAnswer` already use, and `common` exists
  precisely to hold a value type once more than one module needs it. `difficulty` is deliberately
  its own local enum (`enums.Difficulty`: EASY/MEDIUM/HARD), **not** `common.enums.QuestionDifficulty`
  (BEGINNER/INTERMEDIATE/ADVANCED) — a coincidental three-value shape is not the same concept; that
  enum was shared to `common` specifically for `content-service`'s/`ai-service`'s Q&A
  knowledge-level filtering, a different domain. See each enum's own Javadoc.
- `enums/` — `Difficulty`, `ParamType` (the closed value-shape vocabulary every method signature and
  every `harness.LanguageHarness` is restricted to — see its own Javadoc for exactly why it's
  closed), `ProgrammingLanguage` (plain `JAVA`/`PYTHON`/`JAVASCRIPT` — deliberately carries **no**
  vendor-specific detail like a Judge0 `language_id`; that mapping lives in
  `config.JudgeClientProperties#getLanguageIds()` instead, since it's Judge0-specific and this enum
  is used well beyond the judge subsystem — see the enum's own Javadoc. Extend this enum, plus a
  matching `harness.LanguageHarness` bean and a `language-ids` config entry, to add a language),
  `SubmissionStatus` (the full judging vocabulary — Phase 1 only ever produced `PENDING`; Phase 2's
  `SubmissionJudgeEventListener` now actually produces every other value too).
- `harness/` — turns a submission's method body into a full program Judge0 can run. `LanguageHarness`
  (abstract, **Template Method**: `buildProgram` is the fixed skeleton — prelude, user code, a
  generated `main` — with `renderPrelude`/`renderMain` as the per-language steps) has one concrete
  subclass per `ProgrammingLanguage` — `JavaLanguageHarness`, `PythonLanguageHarness`,
  `JavaScriptLanguageHarness` — which simultaneously serve as the **Strategy** half of the design:
  `LanguageHarnessRegistry` selects the right one per submission's declared language. `JavaLanguageHarness`
  is the one with real complexity: Judge0's Java runtime has no application classpath (no Jackson),
  so it carries a hand-rolled, closed-vocabulary JSON parser/writer (`JsonMini`) embedded verbatim
  into every generated Java program — Python's `json`/JavaScript's `JSON` are stdlib/native, so
  those two harnesses need no equivalent. **Verified against the real JDK 21 compiler and a real
  Node runtime in this session** (not just read-through) — the exact generated `JsonMini` class, a
  full generated two-sum `Main.java`, and the generated JavaScript harness were all extracted,
  compiled/run standalone, and produced the correct `[0,1]` output; the Python harness was not
  executed in this session (its logic — `json.loads`/`*args`-unpack/`json.dumps` — is stdlib-trivial
  by comparison), so treat it with the same "read-through only" caution as any other
  unrun-in-this-session code path.
- `judge/` — `JudgeClient` (an **Adapter**, Structural pattern, in front of Judge0's HTTP API) +
  `judge.impl.Judge0Client` (the `RestClient`-backed implementation, works unmodified against
  either Judge0 CE's hosted RapidAPI instance — the default, see the "Phase 2" section below — or a
  self-hosted one: submits with `base64_encoded=true` so arbitrary source/stdin bytes never need
  JSON-string escaping over the wire, adds `X-RapidAPI-Key`/`X-RapidAPI-Host` headers only when
  `JudgeClientProperties#getRapidApiKey()` is set, then polls `GET /submissions/{token}` per
  `JudgeClientProperties`' interval/attempt bounds) + `Judge0Status` (Judge0's status vocabulary
  narrowed to what this module acts on — see its own Javadoc for why `ACCEPTED` here never means
  "matched expected output": this module never sends Judge0's own `expected_output` field, doing
  its own structural JSON comparison instead) + `Judge0SubmissionResult`.
- `event/` — `SubmissionCreatedEvent` (published by `SubmissionServiceImpl.create`) +
  `SubmissionJudgeEventListener` (extends `infra.event.AsyncEventHandler`, but listens via
  `@TransactionalEventListener(phase = AFTER_COMMIT)` + explicit `@Async("asyncEventExecutor")`
  rather than this reactor's usual `@EventHandler` composed annotation — see the listener's own
  Javadoc for why: `@EventHandler` fires immediately on publish, which races the still-open
  publishing transaction; `AFTER_COMMIT` removes that race entirely). Judges a submission against
  every `TestCase` in order, stopping at the first failure; splits its work across two short
  `TransactionTemplate`-scoped transactions (load-and-mark-`RUNNING`, then save-the-final-outcome)
  around a long, deliberately non-transactional middle (the Judge0 round-trips themselves) — see
  the listener's own Javadoc for why holding one long transaction across every test case's judging
  would be a real problem (an open DB connection/locks for the whole run).
- `repository/` (+ `repository/spec/ProblemSpecification`) — Spring Data repositories and dynamic
  filtering, same pattern as every other module's `repository/spec/` package.
- `service/` — `ProblemService`/`SubmissionService` (+ `impl/`), `ProblemCommands`/
  `SubmissionCommands` — services return entities, never this module's own `dto/` classes (same
  rule as `content-service`'s `ArticleService`).
- `api/` (interfaces) + `api/impl/` (controllers) — `ProblemApi` (admin CRUD), `PublicProblemApi`
  (public browsing + `/starter-code`), `SubmissionApi` (owner-gated). `mapper/` —
  `ProblemMapper`/`SubmissionMapper` (MapStruct).
- `exception/DevPracticeErrorCode` — `PROBLEM_*`/`SUBMISSION_*` codes, implements `common`'s
  `ErrorCode` interface.

Full detail: `docs/PROJECT_STRUCTURE.md`'s `## dev-practice-service` section.

## Rules specific to this module

- **No local `User` copy — `Problem.authorUuid`/`Submission.userUuid` are plain columns, never a
  `@ManyToOne User` foreign key.** Same "Option C" shape as every other standalone service's
  owner/author column in this reactor (see root `CLAUDE.md`'s Security section) — this module only
  ever needs "who is the caller," never another user's profile data.
- **A public caller can never see a hidden test case's expected output, and can never confirm the
  existence of a non-`PUBLISHED` problem.** `ProblemService#getPublishedBySlug` filters to
  `PUBLISHED` server-side and throws the same `PROBLEM_NOT_FOUND` whether the slug doesn't exist or
  the problem just isn't published yet — never a distinguishable "found but not visible" response.
  `ProblemMapper#toPublicResponse` strips every `TestCase` with `sample = false` before the response
  ever reaches a public controller. `SubmissionService#create` applies the same
  not-found-if-not-published check before accepting a submission against a problem. When extending
  either endpoint, preserve this — don't add a path that returns a `Problem`/its test cases without
  going through one of these two gates.
- **Services never accept/return this module's own `dto/` classes** — same rule as
  `content-service`'s `ArticleService`. Use `ProblemCommands`/`SubmissionCommands` records for
  multi-field service input instead of threading a `Create*Request`/`Update*Request` into the
  service layer.
- **`TestCase`s and `MethodParameter`s are both supplied inline as real content on
  `CreateProblemRequest`/`UpdateProblemRequest`, never by id reference** — unlike `content-service`'s
  tag-id-list pattern (which references *existing* rows), neither a problem's test cases nor its
  signature parameters exist independently of the problem that owns them. Both are replace-all on
  `update` (`orphanRemoval = true` deletes whatever isn't resupplied). A `MethodParameterRequest`
  carries no `position` field — position is derived from the parameter's index in the request's own
  list (`ProblemController.toParameterInputs`), never client-specified.
- **`TestCase.input`/`expectedOutput` are JSON, not raw stdin/stdout text** — `input` is a JSON
  array of argument values in `Problem.parameters` order (e.g. `[[2,7,11,15], 9]`); `expectedOutput`
  is a single JSON-encoded value of `Problem.returnType`'s shape (e.g. `[0,1]`). Get the numeric
  representation right when authoring a test case for an array/number return type — `[0,1]` and
  `[0.0,1.0]` do not compare equal (see `SubmissionJudgeEventListener#matches`'s Javadoc).
- **A `PUBLISHED` problem's grading contract is split into two independently-governed halves —
  `ProblemServiceImpl` enforces both:**
  - **`methodName`/`returnType`/`parameters` are frozen once a problem is (and stays) `PUBLISHED`.**
    `ProblemServiceImpl#update` compares the incoming signature against what's currently persisted
    (`signatureChanged`) whenever `prevStatus == PUBLISHED && newStatus == PUBLISHED`, throwing
    `PROBLEM_SIGNATURE_LOCKED` on any difference. The escape hatch is moving the problem to
    `DRAFT`/`ARCHIVED` in the *same* update request — the check only fires when the problem stays
    `PUBLISHED` through the call, so unpublish-and-edit is one call, not two. Reason: a signature
    change invalidates every already-submitted `sourceCode`'s ability to compile/run and every
    existing `TestCase`'s JSON encoding — this is the one part of the contract genuinely unsafe to
    change live.
  - **`testCases` are deliberately never locked by publish status** — add/edit/remove freely,
    published or not. Unlike the signature, a test-case edit never invalidates already-submitted
    source code (only ever changes what counts as correct *going forward*), so there's no
    correctness reason to require unpublishing first, and real judges (LeetCode et al.) add test
    cases to live problems routinely. What *is* enforced unconditionally (`validateTestCaseArity`,
    on every create/update, published or not): every `TestCase.input` must parse as a JSON array
    whose length equals the *final* `parameters` list's size — `PROBLEM_TEST_CASE_ARITY_MISMATCH`
    otherwise. This is what actually protects against a test case silently drifting out of sync
    with a (possibly frozen) signature, now that the two aren't locked together.
  - **Accepted, unsolved trade-off:** editing/removing a `TestCase` never retroactively re-judges
    any `Submission` already graded against the old set — a `Submission`'s `passedTestCases`/
    `totalTestCases`/`status` are a one-time snapshot from whenever `SubmissionJudgeEventListener`
    actually ran (see `Submission`'s own Javadoc), so a previously-`ACCEPTED` submission stays
    `ACCEPTED` even if the test data that accepted it changes later. Same behavior every real
    competitive-judge platform has; not something this module tries to solve.
- **Never add a `ParamType` without adding matching support in all three `LanguageHarness`
  implementations** (declaration syntax, JSON parse, JSON write) — the vocabulary is closed
  precisely because every harness has to hand-render support for exactly what it contains, not a
  superset. See `ParamType`'s own Javadoc.
- **Judge0's own `expected_output`/`WRONG_ANSWER` machinery is never used** — this module always
  submits without `expected_output` and does its own structural JSON comparison against
  `TestCase.expectedOutput` in `SubmissionJudgeEventListener`. Don't "simplify" by switching to
  Judge0's own comparison; it does a raw string compare, which would make whitespace-only
  differences (`[0, 1]` vs `[0,1]`) fail incorrectly.
- **No vendor-specific execution-backend detail belongs on `ProgrammingLanguage` (or any other
  domain enum) — it belongs in `judge/impl/`'s own config/mapping.** `judge0LanguageId` used to be a
  field on `ProgrammingLanguage` itself; moved to `JudgeClientProperties#getLanguageIds()`
  (`app.judge0.language-ids.*`) once it became clear that polluted a domain enum used well beyond
  the judge subsystem with one specific vendor's ids, and would have forced a change to that enum on
  any future judge-backend swap — the same discipline `Judge0Status` already applies to submission
  statuses (Judge0's raw status ids never touch `SubmissionStatus` either). `Judge0Client`'s
  constructor fails fast at startup if the configured map is missing an entry for any
  `ProgrammingLanguage` constant, rather than letting a missing id surface later as a confusing
  per-submission failure — keep that check if this mapping mechanism ever changes shape.
- **A new event listener that needs to re-query a row from the publishing transaction must use
  `@TransactionalEventListener(phase = AFTER_COMMIT)`, not this reactor's usual `@EventHandler`** —
  see `SubmissionJudgeEventListener`'s Javadoc for the exact race `@EventHandler` alone would hit
  here. `@EventHandler` (plain `@EventListener` + `@Async`) is still correct for a listener that
  doesn't need read-your-own-writes consistency against the publishing transaction (e.g. one that
  only logs, like `social-service`'s `FriendRequestSentEventListener`).

## Phase 2: submission judging (built)

Design agreed in Phase 1 planning (see the `project_dev_practice_service_module` memory) and now
implemented as described in "What lives here" above: `JudgeClient` Adapter → Judge0,
`LanguageHarness` Strategy+Template Method for per-language program generation, async dispatch via
`infra`'s `AsyncEventThreadPoolConfig`.

**Judge0 backend: hosted RapidAPI, no self-hosted stack scaffolded right now.** Originally built
self-hosted-only (per the Phase 1 plan), then switched to defaulting at Judge0 CE's hosted RapidAPI
instance (`https://judge0-ce.p.rapidapi.com`) once actually deciding how to bring the stack up in
this session — specifically to sidestep Judge0's `isolate` sandbox's known
Docker-Desktop-on-Windows/WSL2 cgroup friction while the judging pipeline itself was still being
verified end-to-end. A self-hosted `judge0-*` stack (`judge0-db`/`judge0-redis`/`judge0-server`/
`judge0-workers` + `docker/judge0/judge0.conf`) was added to `docker-compose.infra.yml` as a kept
fallback, then removed outright shortly after on request, once RapidAPI became the only backend
actually in use — no point carrying ~50 lines of never-run compose config for a path nothing
exercises. **`JudgeClientProperties`/`Judge0Client` still support a self-hosted instance
unmodified**, though, if one is ever reintroduced: `rapidApiKey` set (and `baseUrl` left at
RapidAPI's URL) sends the `X-RapidAPI-Key`/`X-RapidAPI-Host` headers; `rapidApiKey` blank (and
`baseUrl` repointed at a self-hosted instance's own URL) sends no auth header at all — reintroducing
self-hosting is a `docker-compose.infra.yml`/`judge0.conf` addition (Judge0's own official
`docker-compose.yml` template is the reference) plus a config change, not a code change.
`docker-compose.apps.yml`'s `dev-practice-service` container reads `JUDGE0_RAPIDAPI_KEY` from the
host shell (same pattern as `OPENAI_API_KEY`). **Not run against a live Judge0 API call of either
kind in this session** — the `JudgeClient`/harness code itself *was* verified (see "What lives
here" above), just never against a real Judge0 response.

**Not built, deliberately deferred past this phase:**
- Result delivery is polling only (`Judge0Client` blocks internally on `GET /submissions/{token}`)
  — the webhook-callback alternative discussed in Phase 1 planning was explicitly not chosen this
  round (simpler, no new inbound-auth surface to design).
- No admin UI/seed data for authoring problems with real method signatures and test cases yet — the
  REST API is complete, but there's no GUI page and no seeder.
- `Submission` result columns (`passedTestCases`/`totalTestCases`/`errorMessage`) are surfaced on
  `SubmissionResponse` but there's no polling/websocket push to the GUI for "judging finished" —
  a client has to re-`GET /api/v1/submissions/{id}` to see a status change.
- LeetCode-style structural types beyond `ParamType`'s current vocabulary (linked lists, trees,
  generic objects) aren't supported — see `ParamType`'s Javadoc; adding one means extending the
  vocabulary and all three harnesses together, not a small change.
