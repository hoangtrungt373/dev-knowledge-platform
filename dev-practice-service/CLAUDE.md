# CLAUDE.md — dev-practice-service

Module-local guidance for `dev-practice-service`. Read alongside the root `CLAUDE.md`.

## What lives here

A LeetCode/NeetCode-style coding practice platform: a problem catalog (Phase 1, this module's
current scope) and code submission/judging (Phase 2, planned — see "Planned: Phase 2" below).
Package root: `com.ttg.devknowledgeplatform.devpractice.*`.

**Built directly as a standalone Spring Boot application from day one** — like `dev-utils-service`,
never embedded in `gateway` at all, so it is not part of the (closed) microservices-extraction-plan
project (see root `CLAUDE.md`'s Long-term direction section). Its own `DevPracticeServiceApplication`
entry point, its own `dev_practice` Postgres schema (same `dev-premier` database, not a separate
instance — per-service-per-schema, see root `CLAUDE.md`'s Database Conventions), its own port
(`8088`), and its own Liquibase changelog (`DKP-0052`, a fresh snapshot, not a replay of any other
module's history). Routed through `gateway`'s `routing/GatewayRoutesConfig`
(`devPracticeServiceRoutes()`) — `/api/v1/admin/problems/**` and `/api/v1/public/problems/**` are
two more resource segments under the already-shared `/api/v1/admin/**`/`/api/v1/public/**`
prefixes; `/api/v1/submissions/**` is a genuinely new top-level prefix.

- `DevPracticeServiceApplication` — `@SpringBootApplication` + `@Import({JacksonConfig.class,
  TraceContextFilter.class, SlugServiceImpl.class, KeycloakRealmRoleConverter.class,
  KeycloakJwtAuthenticationConverter.class, CurrentUserIdArgumentResolver.class,
  GlobalExceptionHandler.class})` entry point — names the exact `infra` beans this module uses
  instead of a broad `@ComponentScan`/`@ConfigurationPropertiesScan` into the sibling `infra`
  package (see root `CLAUDE.md`'s "Post-extraction hardening" section for the three-round bug
  history that established this convention). `SlugServiceImpl` is imported for `Problem` slug
  generation, the same mechanism `content-service` uses for `Category`/`Tag` slugs.
  **`AsyncEventThreadPoolConfig` is deliberately not imported** — no `@EventHandler` is dispatched
  in this module yet; Phase 2's judging pipeline is the first thing here that will need it (see
  below). **No `@EntityScan`/`@EnableJpaRepositories`** — this module doesn't touch
  `common.entity.User`/`common.repository.UserRepository` at all (see "No local `User` copy" below).
- `security/SecurityConfig` — this app's own filter chain, independent of `gateway`'s (mirrors
  `content-service`'s three-way split): `/api/v1/public/**` (published-problem browsing) permits
  all, `/api/v1/admin/**` (problem-catalog CRUD) requires `ROLE_ADMIN`, everything else
  (`/api/v1/submissions/**`) requires authentication only — ownership, not role, scopes a caller to
  their own submissions.
- `config/web/WebMvcConfig` — registers `infra.security.CurrentUserIdArgumentResolver` as a
  `HandlerMethodArgumentResolver` so `@CurrentUserId String`-annotated controller parameters
  actually resolve; `@Import`ing the bean alone (in `DevPracticeServiceApplication`) isn't enough —
  Spring MVC only picks up argument resolvers registered through a `WebMvcConfigurer`, same as
  every other standalone service's own `config/web/WebMvcConfig`.
- `entity/` — `Problem`, `TestCase` (`@ManyToOne` onto `Problem`, cascade `ALL` +
  `orphanRemoval`, same shape as `task-service`'s `Task.subtasks`), `Submission`. `Problem` reuses
  `common.enums.ContentStatus` (DRAFT/PUBLISHED/ARCHIVED) for its publish lifecycle — the exact
  three-state shape `content-service`'s `Article`/`QuestionAnswer` already use, and `common` exists
  precisely to hold a value type once more than one module needs it. `difficulty` is deliberately
  its own local enum (`enums.Difficulty`: EASY/MEDIUM/HARD), **not** `common.enums.QuestionDifficulty`
  (BEGINNER/INTERMEDIATE/ADVANCED) — a coincidental three-value shape is not the same concept; that
  enum was shared to `common` specifically for `content-service`'s/`ai-service`'s Q&A
  knowledge-level filtering, a different domain. See each enum's own Javadoc.
- `enums/` — `Difficulty`, `ProgrammingLanguage` (JAVA/PYTHON/JAVASCRIPT today — extend this enum,
  plus a matching judging Strategy in Phase 2, to add a language), `SubmissionStatus` (the full
  eventual vocabulary is defined now, even though Phase 1 only ever produces `PENDING` — see
  `Submission`'s Javadoc).
- `repository/` (+ `repository/spec/ProblemSpecification`) — Spring Data repositories and dynamic
  filtering, same pattern as every other module's `repository/spec/` package.
- `service/` — `ProblemService`/`SubmissionService` (+ `impl/`), `ProblemCommands`/
  `SubmissionCommands` — services return entities, never this module's own `dto/` classes (same
  rule as `content-service`'s `ArticleService`).
- `api/` (interfaces) + `api/impl/` (controllers) — `ProblemApi` (admin CRUD), `PublicProblemApi`
  (public browsing), `SubmissionApi` (owner-gated). `mapper/` — `ProblemMapper`/`SubmissionMapper`
  (MapStruct).
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
- **`TestCase`s are supplied inline as real content on `CreateProblemRequest`/`UpdateProblemRequest`
  (a nested `List<TestCaseRequest>`), never by id reference** — unlike `content-service`'s tag-id-list
  pattern (which references *existing* rows), a problem's test cases don't exist independently of
  the problem that owns them. `update` is replace-all: the supplied list becomes the problem's full
  test-case set (`orphanRemoval = true` deletes whatever isn't resupplied).

## Planned: Phase 2 (submission judging — not built yet)

Phase 1 (this module's current state) only ever persists a `Submission` as `PENDING` — there is no
judging pipeline. The agreed design for the follow-up phase (see the `project_dev_practice_service_module`
memory for the full discussion):

- **`JudgeClient`** — an **Adapter** (Structural pattern) in front of a self-hosted
  [Judge0](https://judge0.com) instance (submit code + stdin, poll/webhook for a result). Chosen
  over rolling a custom Docker-per-submission sandbox for the MVP: Judge0 already solves
  compile/run/compare, resource limits, and per-language sandboxing; building that from scratch is a
  real security-engineering project, not a weekend, and would be premature before Judge0 actually
  proves insufficient. The adapter boundary is exactly what makes that later swap possible without
  touching this module's domain code.
- **Strategy** (Behavioral) — a per-`ProgrammingLanguage` compile/run config (compile command, run
  command, file extension), selected by a submission's declared language.
- **Template Method** (Behavioral) — the compile → run → compare-output → score sequence is
  identical across languages; only the compile/run steps (supplied by the Strategy above) vary.
- Judging should be async, dispatched via `infra`'s `AsyncEventThreadPoolConfig` — the same
  `@EventHandler` pattern `ai-service`/`social-service` already use — rather than blocking the
  submit request on an external judge call. This is the point at which this module's entry point
  will start importing `AsyncEventThreadPoolConfig`.
- New `Submission` columns (pass/fail counts, execution time, memory) will land via a new dated
  changeset when Phase 2 actually needs them — deliberately not added speculatively in the Phase 1
  changeset.
