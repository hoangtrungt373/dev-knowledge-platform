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
  - Planned follow-up (Phase 2, not built yet): submission judging via a `JudgeClient` Adapter in
    front of a self-hosted Judge0 instance, a Strategy per `ProgrammingLanguage`, and a Template
    Method for the compile → run → compare → score pipeline, dispatched async via `infra`'s
    `AsyncEventThreadPoolConfig`. See `dev-practice-service/CLAUDE.md`'s "Planned: Phase 2" section.

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
