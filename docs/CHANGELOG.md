# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

`0.0.1` is the original monolith; `0.0.2` is when the break-up into standalone microservices
began (the full six-service extraction — `ecommerce-service`, `identity-service`, `task-service`,
`social-service`, `content-service`, `ai-service` — plus `gateway`'s routing/CORS consolidation and
the reactor-wide `@ComponentScan` fix); `0.0.3` is `ecommerce-service`'s feature build-out (checkout
shipping/coupon pricing strategies, payment countdown + reconciliation) and its `gui` counterpart
work that accumulated under `[Unreleased]` afterward — cut into a real release for the same reason
`0.0.2` was: this file had grown past ~3700 lines under a single ever-growing `[Unreleased]`
section again. Full unabridged entry-by-entry history for all three lives in
[`CHANGELOG-ARCHIVE.md`](CHANGELOG-ARCHIVE.md). New entries start fresh below `[Unreleased]`.

---

## [Unreleased]

### Added

- **New module `dev-utils-service` — a stateless developer-utility API (JSON format/validate,
  YAML↔JSON conversion, HTML beautify), own port `8087`.** Unlike every other standalone service in
  this reactor, this one was built directly standalone from day one, never embedded in `gateway` at
  all — not part of the (closed) microservices-extraction-plan project. It's also the one
  deployable with genuinely nothing to persist and no authenticated caller: no Postgres schema, no
  JPA entity, no Liquibase changelog, and every endpoint public (`security.SecurityConfig`'s own
  `.anyRequest().permitAll()`, replacing Spring Boot's autoconfigured HTTP Basic default). It still
  carries `spring-boot-starter-security` on its classpath regardless of authenticating no one — a
  real finding made while scaffolding it: `common`'s shared `GlobalExceptionHandler` declares
  `@ExceptionHandler` methods over `org.springframework.security.access.AccessDeniedException`/
  `org.springframework.security.core.AuthenticationException`, which Spring resolves via reflection
  when that bean registers at context startup; without the dependency present (which `common`
  declares `optional=true`, so it isn't inherited for free the way every other service's own JWT
  dependency already provides it), that bean fails to load with a `NoClassDefFoundError` before the
  app ever serves a request.
  - `service.DevUtilOperation` — a GoF **Strategy** (Behavioral) interface, one implementation per
    operation (`JsonFormatOperation`, `YamlToJsonOperation`, `JsonToYamlOperation`,
    `HtmlBeautifyOperation`), each its own Spring bean injected by concrete type into
    `api.impl.DevUtilsController` — chosen over a flat facade specifically because more operations
    (Base64, UUID generation, regex test, JWT decode) are a likely next step for this module, not a
    closed door. `HtmlBeautifyOperation` uses jsoup (new dependency, version-managed in the root
    `pom.xml`); YAML support uses Jackson's `jackson-dataformat-yaml` (no explicit version — managed
    by `spring-boot-dependencies`' own Jackson BOM).
  - `exception.DevUtilsErrorCode` — `INVALID_JSON`/`INVALID_YAML` only; no `INVALID_HTML`, since
    jsoup's parser is deliberately lenient and never throws on malformed markup.
  - `api.DevUtilsApi`/`api.impl.DevUtilsController` — `POST /api/v1/dev-utils/{json/format,
    yaml-to-json,json-to-yaml,html/beautify}`, one shared `DevUtilRequest`/`DevUtilResponse` record
    pair for every operation (the shape really is identical across all four).
  - `gateway` wiring: `routing.GatewayRoutesConfig` gained a new `devUtilsServiceRoutes()` bean
    (`/api/v1/dev-utils/**`) and `routing.GatewayServicesProperties` a matching
    `devUtilsServiceBaseUrl`, in both `application.yml` (localhost default) and
    `application-docker.yml` (Compose DNS name). `security.SecurityConfig` gained a new
    `.requestMatchers("/api/v1/dev-utils/**").permitAll()` rule — `gateway` gates `/api/v1/**`
    behind `.anyRequest().authenticated()` before ever proxying anywhere, so a public downstream
    service needs its own carve-out at that layer too, the same two-layer reasoning
    `identity-service`'s own registration-endpoint carve-out already established.
  - `docker-compose.apps.yml` gained a `dev-utils-service` container block — no
    `SPRING_DATASOURCE_*`/`KEYCLOAK_ISSUER_URI` env vars and no `depends_on` at all (not even
    `services-liquibase`), since there is nothing here for it to migrate and no other container it
    needs up first. Every other service's own `Dockerfile` gained a new
    `COPY dev-utils-service/pom.xml dev-utils-service/pom.xml` line in its poms-only stage — Maven
    needs every module's `pom.xml` present to parse the root reactor's `<modules>` list, even for a
    build that never touches this module's own sources.
  - Root `pom.xml`: new module registered, new internal `dependencyManagement` entry, new
    `jsoup.version` property + managed dependency.
  - **Follow-up: `YamlToJsonOperation`/`JsonToYamlOperation` now inject a shared `YAMLMapper` bean
    (new `config.YamlMapperConfig`) instead of each constructing its own `new YAMLMapper()`**, per
    request — the YAML-side counterpart to `infra`'s shared `ObjectMapper` (`JacksonConfig`).
    Mirrors that bean's own customization (`JavaTimeModule`, tolerant deserialization, ISO-8601
    dates) for consistency, even though neither operation can currently observe a difference (both
    work over a generic `JsonNode` tree, never a typed POJO) — kept anyway so a future operation
    that does deserialize into a typed object doesn't hit a silent inconsistency between the two
    mappers. Lives in this module, not `infra` — it's the only consumer today.
  - **Follow-up: a `minify` flag on the shared `DevUtilRequest`, per request** — compact/
    single-line output instead of pretty-printed (the default, `false`, when omitted — non-breaking
    for any existing caller). `service.DevUtilOperation#execute` gained a `boolean minify`
    parameter; `JsonFormatOperation`/`YamlToJsonOperation` honor it on their JSON output
    (`objectMapper.writeValueAsString` vs. `.writerWithDefaultPrettyPrinter()`);
    `HtmlBeautifyOperation` maps it to jsoup's own `prettyPrint(false)` mode (not a true
    single-line guarantee — whitespace already present inside a source text node is preserved
    as-is, the standard content-safe way jsoup distinguishes formatted from unformatted output).
    **`JsonToYamlOperation` accepts but ignores it** — `jackson-dataformat-yaml` has no supported
    single-line/flow-style toggle, so there's no safe way to produce a compact YAML document;
    output is always the same block-style YAML regardless of the flag.
  - **Follow-up: `DevUtilOperation`/the shared request DTOs were corrected away from a forced
    uniform shape, per a direct question about future operations (Unix Time Converter, Number Base
    Converter) that wouldn't fit it.** The `minify` follow-up above had forced every operation
    through one `execute(String input, boolean minify): String` signature and one shared
    `DevUtilRequest`/`DevUtilResponse` DTO pair — reasonable while every operation really was "text
    in, a minify flag, text out," but a genuinely different-shaped future operation (a timestamp+
    timezone+format, or a value+two integer bases) wouldn't fit either, and forcing it through would
    mean hand-packing multiple values into one string instead of real typed parameters.
    `DevUtilOperation` is now a bare **marker interface** (no method at all) — the same "Find
    Implementations" role `infra.event.ApplicationEventHandler`/`infra.service.seed.Seeder` already
    play in this reactor — since nothing dispatches through it polymorphically anyway (the
    controller always calls each operation by its own concrete type). New `dto.MinifiableTextRequest`
    (`input`/`minify`) replaces the shared `DevUtilRequest` for the three operations that genuinely
    share that shape (`json/format`, `yaml-to-json`, `html/beautify`); new `dto.TextRequest`
    (`input` only) replaces it for `json-to-yaml`, which never had a minify concept — dropped
    entirely from that operation's own `execute` signature rather than kept as an ignored
    parameter. `DevUtilResponse` stays shared across all four today, documented as not a rule going
    forward. Old `dto.DevUtilRequest` deleted outright.
  - **Follow-up: unit test suite for all four operations, per request.** One plain JUnit 5 class
    per operation (`JsonFormatOperationTest`, `YamlToJsonOperationTest`, `JsonToYamlOperationTest`,
    `HtmlBeautifyOperationTest`) — no Mockito, each constructs real `ObjectMapper`/`YAMLMapper`
    instances rather than mocking Jackson, since the point is verifying real parse/serialize
    behavior: pretty-vs-minified output, malformed-input rejection with the correct
    `DevUtilsErrorCode`, round-trip structural equality (`readTree` comparison, avoiding brittle
    exact-string assertions against YAML's own quoting/marker formatting), and jsoup's
    lenient-parsing/indent behavior. 12 tests total, verified via a real
    `mvn -pl dev-utils-service -am test` run (JDK 21) — this module's first real runtime
    verification of any kind since it was scaffolded.
  - **Bug fix, found by an actual boot attempt: the app could not start at all.** New
    `DevUtilsServiceApplicationTests` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` +
    `@AutoConfigureMockMvc`) — a context-load + end-to-end `MockMvc` smoke test hitting all four
    endpoints with no `Authorization` header — immediately failed with
    `DataSourceBeanCreationException: Failed to determine a suitable driver class`. Root cause:
    `common` declares `spring-boot-starter-data-jpa` as a **non-optional** dependency (needed there
    for `AbstractEntity`'s `@MappedSuperclass`/`@Entity` support), which every consumer inherits
    transitively regardless of whether it maps any entities — every other service in this reactor
    never notices because each one already configures a real `spring.datasource.url` +
    `org.postgresql:postgresql`; this module deliberately has neither. Fixed with
    `@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class})` on `DevUtilsServiceApplication`. Re-ran: 19/19 tests
    pass, including all four endpoints returning `200` with zero authentication and a malformed-
    input case correctly returning `400` with `DEVUTILS_001` through the shared
    `GlobalExceptionHandler`. This is the module's first genuine confirmation that it actually
    boots — every claim in its own `CLAUDE.md` about "no schema, no auth" had, until this test,
    only ever been verified by static reasoning, not by starting the app.
  - **Follow-up: an `input` size cap, per request — a deliberately generous but finite first-version
    bound (`100_000` characters), since this is the one fully public, unauthenticated endpoint in
    the reactor.** New `dto.DevUtilsLimits.MAX_INPUT_LENGTH`, referenced by a new
    `@Size(max = ...)` on both `MinifiableTextRequest.input`/`TextRequest.input` (alongside the
    existing `@NotBlank`) — one shared constant rather than each DTO guessing its own number.
    Verified via two new `DevUtilsServiceApplicationTests` cases: input one character over the cap
    is rejected `400` by Bean Validation before ever reaching an operation; input at exactly the
    cap is accepted. The boundary test's own first draft used a 100,000-digit run (simultaneously
    valid JSON — a single large integer literal — and trivial to size exactly), which incidentally
    tripped a *different*, pre-existing Jackson safety limit
    (`StreamReadConstraints.getMaxNumberLength()`, default 1000 digits per JSON number token) once
    `JsonFormatOperation` tried to parse it — not a bug in the new `@Size` cap, just the wrong test
    fixture; switched to a JSON array wrapping one long string (`["aaa...a"]`) to sidestep it. 21
    tests total, verified via a real `mvn -pl dev-utils-service -am test` run (JDK 21).
  - **Follow-up: `gui` feature (`@dev-utils`), per request — one page, `/dev-utils`, genuinely
    public (no `PrivateRoute`, mirroring `/shop`'s existing precedent) with an MUI `Tabs`-based UI,
    one tab per operation, each tool additionally getting its own shareable/bookmarkable URL via
    the hash (`/dev-utils#json-format`, `#yaml-to-json`, `#json-to-yaml`, `#html-beautify`) rather
    than a second-level route.** New `features/dev-utils/{types.ts,api/devUtilsApi.ts,
    components/DevUtilToolPanel.tsx,pages/DevUtilsPage.tsx}` — `devUtilsApi`'s `jsonToYaml` has no
    `minify` parameter at all, matching the backend's own operation shape; `DevUtilToolPanel` is
    the one shared per-tab UI (input `TextField`, optional minify `Checkbox`, `SubmitButton`, a
    read-only output panel via the already-installed `react-syntax-highlighter` Prism/`vscDarkPlus`
    — no new dependency). New `@shared/components/CopyIconButton.tsx` — no copy-to-clipboard
    primitive existed anywhere in this app before this. New `@dev-utils/*` path alias
    (`tsconfig.json` + `vite.config.ts`, kept in sync per this repo's own standing warning about
    the two not sharing config). `App.tsx` gained the `/dev-utils` route (public, in the same
    region as `/shop`); `NavBar.tsx` gained an unconditionally-rendered "Dev Utils" button
    (`CodeIcon`) alongside Shop's, outside the `isAuthed`/`!isAuthed` branches every other button
    lives in. Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in
    this sandbox, so the actual tab-switching/hash-sync/copy/output behavior is unverified in a
    real browser.
  - **Follow-up: the operation list moved from a horizontal MUI `Tabs` bar to a left sidebar, per
    request.** `DevUtilsPage.tsx` now renders a plain `Paper` + `List`/`ListItemButton` sidebar
    (`AccountLayout.tsx`'s own precedent, deliberately not an MUI `Drawer` — same already-diagnosed
    `position: fixed`-by-default bug class that component's own Javadoc documents) with one icon
    (`DataObjectIcon`/`SyncAltIcon`/`SwapHorizIcon`/`AutoFixHighIcon`, each confirmed present in the
    installed `@mui/icons-material` version) per operation, replacing the earlier `Tabs`/`Tab`
    markup. The four `{tab === 'x' && <DevUtilToolPanel .../>}` branches collapsed into one
    `OperationConfig[]` array driving both the sidebar and a single `DevUtilToolPanel` call site,
    keyed by the active operation so switching tools still remounts the panel (resetting its own
    input/output/minify state, unchanged from the prior behavior). The hash-based per-operation
    URLs (`#json-format` etc.) and their `useLocation`/`useNavigate` sync are untouched — only the
    selector UI changed, not the routing mechanism. Verified via a clean `tsc --noEmit` and a
    successful `vite build` only — no Docker in this sandbox, so the actual sidebar
    selection/highlight behavior is unverified in a real browser.
  - **Follow-up: `DevUtilToolPanel.tsx` split into side-by-side Input/Output cards, per request —
    each card's action buttons sit on the same line as its own title.** Input card: "Input" title +
    Paste (text+icon, `navigator.clipboard.readText()`) + the operation's own submit action
    (`SubmitButton`, now with a `PlayArrowIcon`). Output card: "Output" title + Copy (text+icon,
    swaps to "Copied!" for 1.5s) + Download (icon-only, `DownloadIcon` + `Tooltip`) — a new local
    `downloadTextFile` helper (`Blob`/`createObjectURL`/a temporary `<a download>` click) backs the
    latter; each operation now supplies its own `downloadFileName` via a new
    `OperationConfig.downloadFileName` field. `@shared/components/CopyIconButton.tsx` (added last
    pass) was deleted outright once this redesign left it with zero remaining consumers — the new
    Copy button is a full text+icon `Button` built inline instead, a different shape than that
    component supported. Verified via a clean `tsc --noEmit` and a successful `vite build` only —
    no Docker in this sandbox, so the actual paste/copy/download interactions are unverified in a
    real browser.
  - **Follow-up: Minify moved from a `Checkbox` below the input into a toggle button in the Input
    card's own header row, after the action button, per request.** A plain `Button` (not MUI's
    `ToggleButton` — kept as the same component the row's other buttons already use, avoiding a
    second component type's own default styling to reconcile) whose `variant` swaps
    `outlined`↔`contained` to show pressed state (`UnfoldLessIcon`, `aria-pressed={minify}`); still
    omitted entirely, not just disabled, for `json-to-yaml` (no minify concept — see
    `devUtilsApi.jsonToYaml`'s own comment). Verified via a clean `tsc --noEmit` and a successful
    `vite build` only — no Docker in this sandbox, so the actual toggle behavior is unverified in a
    real browser.
  - **Follow-up: 4 changes in one request.** (1) Input/Output card headers uppercased
    (`textTransform: 'uppercase'`, `letterSpacing: 0.5`, `fontWeight: 700`). (2) A new headline
    above the Input/Output panels — category (`'Formatters'`/`'Converters'`, `variant="overline"`)
    → operation title (`h6`) → one-line description (`body2`) — backed by two new
    `OperationConfig` fields (`category`/`description`); `json-format`'s own label renamed "JSON
    Format" → "JSON Format/Validate" in the same pass so the sidebar and headline agree. (3) The
    selected sidebar item now gets an explicit `bgcolor: 'action.selected'` (+ bolder label
    `fontWeight`) instead of relying on `ListItemButton`'s own default `selected` styling — same
    convention `app/NavBar.tsx`'s `NavButton` already uses for an active route. (4) A search
    `TextField` (`SearchIcon` adornment) above the sidebar list, filtering by label/category/
    description — client-side only, only affects what the sidebar shows, never which tool's panel
    is displayed; an empty result shows "No tools found." instead of an empty list. Verified via a
    clean `tsc --noEmit` and a successful `vite build` only — no Docker in this sandbox, so the
    actual search/highlight/headline rendering is unverified in a real browser.
  - **Follow-up: sidebar selection styling reworked, per request — fixed `fontWeight: 600` on
    every item's label (no longer bold-only-when-selected), and the selected background changed
    from `action.selected` to a primary-brand-tinted `alpha(theme.palette.primary.main, 0.16)`
    (`0.24` on hover) via the same `sx={{ bgcolor: (theme) => alpha(...) }}` callback shape
    `@shared/components/UploadingOverlay.tsx` already established for a themed translucent color.**
    Only the background now distinguishes the selected item; text weight no longer varies by
    selection state.
  - **Bug fix, reported directly ("the bgColor does not change when I select the item"): the
    background override above wasn't actually taking effect.** Root cause: `selected={isSelected}`
    (still passed to `ListItemButton` for semantics) makes MUI apply its own baked-in
    `&.Mui-selected { backgroundColor: action.selected }` rule, whose selector (root class +
    `Mui-selected` class) is *more specific* than a plain single-class `bgcolor` override on the
    same component — so MUI's own gray default silently won regardless of the `isSelected`
    conditional already picking the right value in JS. Fixed by targeting
    `&.Mui-selected`/`&.Mui-selected:hover` explicitly inside the `sx` object, matching MUI's own
    selector so the override actually wins — the standard, documented pattern for overriding a
    component's built-in selected-state styling. Verified via a clean `tsc --noEmit` and a
    successful `vite build` only — no Docker in this sandbox, so the actual selected-item
    appearance is unverified in a real browser; worth a manual click-through to confirm the fix.
  - **Follow-up: the operation headline block (category/title/description) is now its own `Paper
    variant="outlined"` card (`bgcolor: 'background.paper'`), matching the Input/Output cards
    below it, per request.** The category line (`'Formatters'`/`'Converters'`) also gained
    `color="primary.main"` and `fontWeight={700}` — a bolder, brand-colored label instead of the
    same gray as the description line beneath it. Verified via a clean `tsc --noEmit` and a
    successful `vite build` only — no Docker in this sandbox, so the actual appearance is
    unverified in a real browser.
  - **Follow-up: the Input card's own `TextField` had its outlined-variant border hidden, per
    request — left as-is, it visibly nested its own bordered box inside the card's `Paper` border
    ("a box inside a box").** `sx` targets `.MuiOutlinedInput-notchedOutline` under all three
    states explicitly (default/`&:hover`/`&.Mui-focused`), not just the base selector — MUI's own
    hover/focus rules for that element carry an extra pseudo-class, so a base-selector-only
    override would have silently lost in those states, the identical specificity gotcha just fixed
    on the sidebar's selected-item background a few entries above. Verified via a clean
    `tsc --noEmit` and a successful `vite build` only — no Docker in this sandbox, so the actual
    borderless appearance across hover/focus is unverified in a real browser.
  - **Follow-up: Sample and Clear buttons on the headline card's right side, per request — Sample
    fills the input with the operation's own placeholder text, Clear empties it (disabled while
    already empty).** Required lifting `input` state out of `DevUtilToolPanel.tsx` (a sibling of
    the headline card, so it can't reach into that component's state) up into `DevUtilsPage.tsx`
    — now a controlled `input`/`onInputChange` pair passed down to the panel, reset to `''` via a
    `useEffect` keyed on `tab`; the panel's own Paste button and `TextField` typing both call
    `onInputChange` instead of a local setter. Every other piece of the panel's own state
    (`minify`/`output`/`saving`/`copied`) stays local, still reset by the existing
    `key={activeOperation.key}` remount on tool switch. Reuses `inputPlaceholder` as the sample
    content rather than a second per-operation sample string. Verified via a clean `tsc --noEmit`
    and a successful `vite build` only — no Docker in this sandbox, so the actual Sample/Clear
    interaction is unverified in a real browser.
  - **Follow-up: the Sample content made richer, per request — a mixed-type example instead of
    the bare `{"foo": "bar"}` reused from the placeholder.** `OperationConfig` gained a separate
    `sampleInput` field (`inputPlaceholder` stays short — it's shown as ghost text inside an empty
    textarea, where a long placeholder reads as cluttered) — reverses the earlier "Sample reuses
    the placeholder, one source of truth" decision now that a demonstrative sample and a brief
    empty-box hint turned out to be different jobs. All four operations share one "Vui Coding"
    project theme (`{"project":"Vui Coding","online":true,"tools":["JSON","Base64","JWT"],
    "stars":128}` for `json-format`/`json-to-yaml`, its YAML equivalent for `yaml-to-json`, an
    equivalent small HTML snippet for `html-beautify`) so the four samples read as one consistent
    example. Verified via a clean `tsc --noEmit`, a Node-side `JSON.parse` sanity check on the two
    JSON sample strings, and a successful `vite build` — no Docker in this sandbox, so the actual
    Sample button's output is unverified in a real browser.
  - **Follow-up: `sampleInput` reversed back out, per request.** `inputPlaceholder` now holds the
    same richer value each operation's `sampleInput` held, and `sampleInput` was deleted outright
    (the field, all four operations' entries, `handleUseSample`) — kept `inputPlaceholder` as the
    surviving field, since it was already threaded through to `DevUtilToolPanel`'s own `TextField`
    `placeholder` prop with no rename needed. One field now serves both the empty-textarea ghost
    text and the Sample button's fill value. Verified via a clean `tsc --noEmit` and a successful
    `vite build` only — no Docker in this sandbox, so the actual placeholder/Sample-button
    rendering is unverified in a real browser.
  - **Follow-up: Clear now also blanks the Output panel, per request.** `output` moved out of
    `DevUtilToolPanel.tsx`'s own local state into `DevUtilsPage.tsx`, controlled the same way
    `input` already is (`output`/`onOutputChange` props) — `handleClearInput` now resets both, and
    the tab-switch reset `useEffect` resets both too. The Clear button's `disabled` check widened
    to `!input && !output`, so a manually-cleared input (typed over, not via the button) with a
    still-present output can still be cleared. Verified via a clean `tsc --noEmit` and a successful
    `vite build` only — no Docker in this sandbox, so the actual behavior is unverified in a real
    browser.
  - **Follow-up: the Output panel's empty-state placeholder now carries the same dark background
    (`#1e1e1e`, `vscDarkPlus`'s own) the syntax highlighter uses once populated, per request** —
    fixes a white → black flash on every run, since the empty state previously rendered on the
    theme's own `Paper` background regardless of the app's light/dark mode. New `OUTPUT_BG_COLOR`
    constant applied to both the placeholder `Box` and an explicit `customStyle.background` on the
    highlighter itself; placeholder text color changed to a fixed `grey.500` to stay legible
    against it. The Input panel's own `TextField` is untouched — still follows the app's own
    light/dark theme. Verified via a clean `tsc --noEmit` and a successful `vite build` only — no
    Docker in this sandbox, so the actual color transition is unverified in a real browser.
  - **Bug fix: the Format/Convert/Beautify button's own width visibly shrank while its request was
    in flight and snapped back on completion, reported directly.** Root cause was in the shared
    `@shared/components/SubmitButton.tsx` (used by ~20 call sites across the app, not specific to
    this feature) — it swapped its `children` outright between `label` and a `CircularProgress`
    spinner while `saving`, and the spinner is narrower than most labels, shrinking the button's
    own content-driven width for the request's duration. Fixed at the shared component: `label` is
    now always rendered (`visibility: hidden` while `saving`, never removed from the flow), so the
    button's width stays driven by the label regardless of `saving`; the spinner renders
    `position: absolute`, centered over it via a new `position: relative` on the `Button` itself.
    Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
    sandbox, so the actual behavior is unverified in a real browser, here or on any of this
    component's other consumers.
  - **Follow-up bug fix: the width-jump fix above centered its spinner on the whole `Button`'s own
    box, which is only correct with no `startIcon` — this panel's action button always has one, so
    the spinner landed visibly left of the label's own position, reported directly as the button
    looking "re-rendered" on click.** Fixed by moving the `position: relative` off `Button` and
    onto a new inner wrapper `Box` around just the label, with the spinner's `position: absolute`
    now targeting that wrapper instead of the whole button — the spinner always lands exactly on
    the label regardless of `startIcon`. Verified via a clean `tsc --noEmit` and a successful
    `vite build` only — no Docker in this sandbox, so the actual fix is unverified in a real
    browser, here or on any of this component's other consumers.
  - **Third follow-up bug fix: the label ⇄ spinner swap was still an instant, untransitioned
    toggle, which read as a literal flash for a fast-resolving request (typical for this feature's
    own local transforms) — reported directly as the button's text blinking.** Fixed by
    cross-fading `opacity` on both the label and the spinner (each with a short `transition:
    'opacity 0.15s ease'`) instead of `visibility`/conditional mounting — the spinner is now always
    mounted, since a mount/unmount can't cross-fade. Added `pointerEvents: 'none'` to the spinner
    so it doesn't sit on top of the label while faded out. Verified via a clean `tsc --noEmit` and
    a successful `vite build` only — no Docker in this sandbox, so the actual cross-fade is
    unverified in a real browser, here or on any of this component's other consumers.
  - **Fourth follow-up bug fix: the actual root cause, outside the label/spinner slot entirely —
    `disabled={saving || disabled}` made `Button` apply MUI's own `.Mui-disabled` styling (a
    genuinely different color scheme from the button's normal contained-primary look) the instant
    `saving` flipped true, so for a fast request the whole button flashed to muted grey and back,
    independent of the label/spinner cross-fade the first three fixes focused on.** Fixed by
    conditionally overriding `&.Mui-disabled`'s own `backgroundColor`/`color` back to
    `primary.main`/`primary.contrastText` via `sx`, but only while `saving` — a real
    `disabled`-for-other-reasons button still gets MUI's normal muted look. Verified via a clean
    `tsc --noEmit` and a successful `vite build` only — no Docker in this sandbox, so the actual
    fix is unverified in a real browser, here or on any of this component's other consumers.
  - **Follow-up, per request: an invalid-input error now renders inline in the Output panel
    instead of a header notification, with a friendlier message than the backend's own raw
    exception text.** `DevUtilsPage.tsx`'s four `onSubmit` closures dropped their `showError`
    argument (suppressing `httpClient`'s toast without touching that shared client); a new
    `error: DevUtilError | null` state, lifted the same way `output` already is, feeds a red-
    outlined box rendered inside the Output panel (`"Cannot be processed"` headline + a monospace
    detail line) instead. New `utils/errorFormatting.ts` (`buildDevUtilError`) supplies the detail:
    for a JSON-input operation (`json-format`/`json-to-yaml`), it re-runs the browser's own
    `JSON.parse(input)` purely to harvest its message — V8's own JSON syntax errors already match
    the requested format verbatim (confirmed via a real Node check, not assumed). For
    `yaml-to-json`/`html-beautify` (no client-side parser available), a `simplifyBackendMessage`
    fallback strips the backend's own parser-internals noise (confirmed, via a background agent
    reading the actual backend code, to be Jackson's `JsonProcessingException.getMessage()` reused
    verbatim — a real, separately-documented backend bug: `DevUtilsErrorCode`'s own
    `"Invalid {0}"`-style templates are defined but never applied, see
    `dev-utils-service/CLAUDE.md`'s new note) down to one line plus a plain "(line N, column M)"
    suffix, tolerant of both Jackson's and SnakeYAML's differing raw formats. Verified via a clean
    `tsc --noEmit`, a successful `vite build`, and a real Node sanity check of the message-cleanup
    logic against both raw shapes plus native `JSON.parse` calls — no Docker in this sandbox, so
    the actual on-screen result is unverified in a real browser.
  - **Follow-up, per direct request ("fix the backend too") — the `dev-utils-service` bug is now
    actually fixed server-side, not just worked around/documented client-side.** New
    `dev-utils-service` class `exception/ParsingExceptionMessages` builds a clean error message
    structurally from a `JsonProcessingException` (`getOriginalMessage()` + the structured
    `getLocation()`, never string-parsed off `getMessage()`), and each of the three JSON/YAML
    operations' catch blocks now passes that message through a `(Object)` cast so
    `BusinessException`'s varargs constructor — the one that actually applies
    `DevUtilsErrorCode.INVALID_JSON`/`INVALID_YAML`'s own `"Invalid {0}"` template — finally gets
    selected instead of the raw-message overload. Verified end-to-end via a real standalone Java
    harness compiled and run against the actual resolved Jackson 2.19.2 jars and this reactor's own
    compiled classes (not just read and trusted): 4 real malformed JSON/YAML inputs each produced a
    clean, single-line, noise-free message, and a full `BusinessException` round trip confirmed the
    `"Invalid JSON: ..."` template now actually applies. `gui`'s own `errorFormatting.ts` was
    updated to match — `simplifyBackendMessage` gained an idempotency guard so it no longer
    re-appends its own location suffix on top of a message the backend now already ends with one 
    (verified via a Node check); the client-side `JSON.parse` path for `json-format`/`json-to-yaml`
    is untouched, since it was always a better message than the backend's own regardless of this
    fix. See `dev-utils-service/CLAUDE.md`'s own updated note for the full fix detail. Verified via
    a clean `tsc --noEmit` and a successful `vite build` on the GUI side and a targeted
    `-pl dev-utils-service -am compile` on the backend side — no Docker in this sandbox, so the
    actual on-screen result through a running backend is unverified in a real browser.
  - **Follow-up, per request: the Output panel's background is now state-driven** — white by
    default and on a failed submit, switching to black only once a real result is showing — plus a
    decorative download icon above the empty placeholder's text. New `OUTPUT_BG_LIGHT`
    (`'#ffffff'`) alongside the renamed `OUTPUT_BG_DARK` (was `OUTPUT_BG_COLOR`), and a new
    `OUTPUT_ERROR_COLOR` (`'#cf222e'`, the light theme's own error red used as a fixed literal
    rather than the `error.main` token, which swaps to a brighter dark-mode red that would clash
    with this panel's now-always-white error background) — all three colors are fixed literals, not
    theme tokens, matching this panel's existing "independent of the app's light/dark toggle"
    precedent. This deliberately reintroduces the white → black transition on a successful submit
    that an earlier fix removed — that fix solved a different problem (a jarring flash between two
    *same-colored* states) which no longer applies now that empty/error and populated are
    intentionally different colors by design. Verified via a clean `tsc --noEmit` and a successful
    `vite build` only — no Docker in this sandbox, so the actual on-screen appearance is unverified
    in a real browser.
  - **Follow-up, per request: a new info row between the Output header and its content area**
    ("File type: <label>" / "File name: <downloadFileName>", both already known statically per
    operation) rendered identically across all three content states, plus `showLineNumbers` on the
    actual response's syntax highlighter (muted line-number color, `userSelect: 'none'` so a select-
    all/copy doesn't grab the line-number column). New `OUTPUT_LANGUAGE_LABELS` maps each
    operation's existing Prism language id to a human label. Verified via a clean `tsc --noEmit`
    and a successful `vite build` only — no Docker in this sandbox, so the actual on-screen result
    is unverified in a real browser.
  - **Follow-up, per request: the info row's labels were dropped (values only), its background
    became a fixed dark shade instead of the theme's `background.paper`, and the two values are
    now horizontally aligned with the code content below them.** New `OUTPUT_INFO_BG`
    (`'#252526'`, a shade lighter than the content area's own `'#1e1e1e'`) + `OUTPUT_INFO_TEXT_COLOR`;
    new `LINE_NUMBER_GUTTER_WIDTH` (`'3.5em'`, matching the syntax highlighter's own
    `lineNumberStyle`) sizes a leading column so the file-type value lines up with the line-number
    column and the file-name value lines up with where the response text starts — both info-row
    `Typography`s switched to `fontFamily: 'monospace'` to match the highlighter's own font, since
    `em`-based alignment only holds between elements sharing a font. Verified via a clean
    `tsc --noEmit` and a successful `vite build` only — no Docker in this sandbox, so the actual
    on-screen alignment/coloring is unverified in a real browser.
  - **Follow-up, 5 fixes per request — clarified first via `AskUserQuestion`** (layout stayed
    side-by-side; "dark" text meant a readable dark-theme tone, not literally low-contrast; the
    vertical divider is confirmed full-height): (1) fixed a baseline mismatch between the file-type
    and file-name values (the same `Typography`-vs-flex-context half-leading mismatch `@tasks`'s
    `TaskRow.tsx` already documents — both values now sit in their own `display: 'flex',
    alignItems: 'center'` wrapper); (2) one continuous vertical gutter-divider line
    (`OUTPUT_LINE_COLOR`, `'#3c3c3c'`) now spans the info row and content area, painted last so it
    draws over their opaque backgrounds; (3) the file-type value's color now varies per language
    (`OUTPUT_LANGUAGE_COLORS` — blue/purple/orange for JSON/YAML/HTML); (4) file-name/line-number
    colors switched to VS Code's own editor/line-number tones (`OUTPUT_FILENAME_COLOR`
    `'#d4d4d4'`/`OUTPUT_LINE_NUMBER_COLOR` `'#858585'`); (5) the info row's border-bottom switched
    from the theme's `divider` token (barely visible against a hardcoded dark background) to the
    same fixed `OUTPUT_LINE_COLOR`. Verified via a clean `tsc --noEmit` and a successful
    `vite build` only — no Docker in this sandbox, so the actual on-screen result is unverified in
    a real browser.
  - **Follow-up, 2 fixes per request, reversing part of the previous pass**: (1) the vertical
    gutter-divider line was removed outright — the `position: absolute`/`relative` wrapper it
    needed is gone too, the info row and content states are direct `Paper` children again; (2)
    file-name and line-number colors both switched to one muted grey (`'#6e7681'`, reused for both
    `OUTPUT_FILENAME_COLOR`/`OUTPUT_LINE_NUMBER_COLOR`), reversing the previous pass's brighter
    VS Code editor-foreground/line-number colors — neither is the focused content, so both are
    deliberately de-emphasized now. The file-type value's per-language color is unaffected.
    Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
    sandbox, so the actual on-screen result is unverified in a real browser.
  - **Follow-up, per request — the info row simplified from two aligned columns down to one plain
    "`<TYPE> | <filename>`" line**, with a concrete template supplied showing exactly what was
    wanted. The `LINE_NUMBER_GUTTER_WIDTH`-wide alignment columns (lining the type/filename values
    up with the line-number/response columns beneath them) are gone, along with that now-unused
    constant — superseded by three plain `Box component="span"`s inside one `Typography` (type
    segment keeps its per-language color and bold weight; separator + filename use the muted
    `OUTPUT_FILENAME_COLOR` grey). Rendering all three as inline spans in one text flow also
    incidentally resolves the earlier baseline-mismatch bug for free. Verified via a clean
    `tsc --noEmit` and a successful `vite build` only — no Docker in this sandbox, so the actual
    on-screen line is unverified in a real browser.
  - **Bug fix, reported directly with the exact wrong color observed ("the output is #6a9955")** —
    `lineNumberStyle`'s `color` had never actually taken effect; line numbers always rendered in
    vscDarkPlus's own comment-token green. Root-caused by reading `react-syntax-highlighter`'s own
    source (not guessed) and confirmed with a server-render harness: the library tags every
    line-number span with a `comment` className, and its style-merge spreads the theme's
    `stylesheet['comment']` color on top of `lineNumberStyle` unconditionally, overwriting it no
    matter what value was passed — confirmed the rendered span's inline style still read
    `color:#6a9955` even with `color` removed from `lineNumberStyle` entirely. Since this is a
    plain (non-`!important`) inline style, only a `!important` CSS rule can still override it —
    fixed by wrapping the `SyntaxHighlighter` in a `Box` targeting the library's own
    `.react-syntax-highlighter-line-number` class via `sx` with an `!important` color. Verified via
    a clean `tsc --noEmit`, a successful `vite build`, and the same server-render harness confirming
    the diagnosis — the fix's actual on-screen effect couldn't be confirmed the same way, since
    server rendering doesn't resolve CSS at all.
  - **Follow-up, per request: the info row now renders only once `output` actually holds a real
    result** — `null` for both the empty-placeholder and `error` states, where it used to show
    unconditionally across all three. A single `{output !== null && (...)}` guard around the
    existing `Stack`, no other change to its own markup/colors. Verified via a clean `tsc --noEmit`
    and a successful `vite build` only — no Docker in this sandbox, so the actual on-screen result
    is unverified in a real browser.
  - See `dev-utils-service/CLAUDE.md` for the full module writeup, and root `CLAUDE.md`'s Module
    Structure table, Long-term direction, Security, Database Conventions, and Architecture →
    Routing sections for the reactor-wide documentation updates this addition required.

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
