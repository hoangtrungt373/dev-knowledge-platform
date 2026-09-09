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
  - **Follow-up: 6 new operations, per request — CSS/LESS/SCSS/JS Beautify+Minify (one endpoint
    per language, `minify` flag same as the existing operations, not separate endpoints), ERB
    Beautify+Minify, and XML Beautify+Minify.** New endpoints, all under the existing
    `/api/v1/dev-utils/**` prefix (`gateway`'s own wildcard route/`permitAll()` already covers a new
    sub-path — no `gateway` change needed): `POST /api/v1/dev-utils/{css,less,scss,js,erb,xml}/beautify`,
    all reusing the existing `MinifiableTextRequest`/`DevUtilResponse` DTOs (every one of the six
    genuinely shares that "text in, minify flag, text out" shape) and a new operation class each
    (`CssOperation`/`LessOperation`/`ScssOperation`/`JsOperation`/`ErbOperation`/`XmlOperation`,
    injected into `DevUtilsController` by concrete type same as every other operation).
    - **CSS/LESS/SCSS/JS share one new textual reformatter, `service.impl.support.
      CurlyBraceFormatter`, rather than four separate implementations or a real per-language
      parser.** There's no single grammar a Java library could parse across all four uniformly
      (LESS/SCSS extend CSS with variables/nesting/mixins a strict CSS parser rejects; JS has its
      own grammar entirely) — a real per-language parser for each is a fundamentally bigger
      undertaking (that's what Prettier/Terser actually do). Instead, this tracks only the
      structural signals all four "curly-brace languages" share: brace-nesting depth,
      statement-ending semicolons, and comment/string literals kept atomic. Same "lenient,
      best-effort, no invalid-input failure path" trade-off `HtmlBeautifyOperation` already makes
      for HTML — none of these four operations have a matching `DevUtilsErrorCode`, since none of
      them can throw. `LessOperation`/`ScssOperation` only reformat — they do not compile LESS/SCSS
      to plain CSS; their own extensions pass through as literal text, untouched.
      - **Known, documented limitation: JavaScript's Automatic Semicolon Insertion (ASI).** A
        textual reformatter with no real JS parser can't know that `return\nx;` means `return; x;`
        (the restricted-production rule after `return`/`break`/`continue`/`throw`) — collapsing
        that line break into a space would silently change what the code returns. Both
        `beautify`/`minify` guard against this generically (never collapse a real line break
        between two ordinary, non-punctuation characters into "nothing" or a plain space — only
        another real newline can replace it), without needing to special-case those keywords by
        name. The practical cost: `minify` doesn't guarantee single-line output for JS the way it
        mostly does for CSS/LESS/SCSS (whose declarations are semicolon/brace-delimited at nearly
        every whitespace boundary already).
      - Also deliberate: `beautify` never normalizes spacing around a bare `:` (e.g. `color:red`
        stays exactly as written) — inserting a space unconditionally would corrupt a pseudo-class
        selector like `:hover`/`::before`, which requires *no* space after the colon; telling a
        declaration's colon from a selector's needs real grammar awareness this formatter
        deliberately doesn't have. `minify` does the opposite, safely: `:` is one of a small set of
        "safe to tighten" punctuation characters (alongside `; { } , ( ) [ ]`) whose surrounding
        whitespace is always droppable regardless of context.
    - **`ErbOperation` reuses `HtmlBeautifyOperation`'s jsoup-based approach**, with one added
      step: every `<%...%>` tag is extracted and replaced with an opaque placeholder before jsoup
      ever parses the input, then restored verbatim afterward. Needed because jsoup HTML-escapes
      text-node content on serialization (a literal `<` becomes `&lt;`), which would otherwise
      corrupt the ERB tag's own delimiters on the way back out, and because protecting the whole
      tag as one unit means the embedded Ruby's own `<`/`>` (e.g. `<% if x < y %>`) never reaches
      jsoup's tokenizer at all. **The placeholder itself went through a real, test-caught fix**:
      control characters (STX/ETX) were tried first on the assumption jsoup only escapes
      `<`/`>`/`&`/quotes — wrong, confirmed by an actual failing test: jsoup's own `Entities`
      serialization also escapes non-printable control codepoints as numeric character references
      (`&#x2;`, not the original byte), which broke the placeholder-matching restore step just as
      badly as leaving ERB tags unprotected would have. Switched to a random alphanumeric marker
      (via `UUID`, generated fresh per call) instead — plain letters/digits are never escaped by any
      HTML serializer.
    - **`XmlOperation` is the one operation in this batch backed by a real grammar parser (JAXP,
      built into the JDK — no new Maven dependency), the same "real parse, real invalid-input
      error" shape `JsonFormatOperation`/`YamlToJsonOperation` already establish** — new
      `DevUtilsErrorCode.INVALID_XML` (`DEVUTILS_003`). Beautify strips whitespace-only text nodes
      then re-serializes via `Transformer`'s indent mode (2-space, matching this module's existing
      convention); minify does the same with indent off — a text node with real (non-blank)
      content is never touched either way, whitespace-only or not. Preserves (or omits) the
      `<?xml ...?>` declaration based on whether the *input* had one, rather than always adding or
      always dropping it. **XXE (XML External Entity) hardening is not optional here** — this is
      one of the fully public, unauthenticated endpoints in this reactor, and a
      `DocumentBuilderFactory` left at JDK defaults will happily resolve a `<!DOCTYPE>`'s external
      entities (a textbook injection vector: a malicious caller's DTD could reference a local file
      or internal URL and have it echoed back in the output). Hardened per the OWASP XXE
      Prevention Cheat Sheet's JAXP baseline: `<!DOCTYPE>` disallowed outright, external general/
      parameter entities and external DTD loading disabled as defense in depth, and the
      `TransformerFactory` used to serialize the result has external DTD/stylesheet access disabled
      too. A custom, silent `ErrorHandler` still rethrows on error/fatal error (unchanged behavior)
      but stops the JDK's default handler from spamming stderr for what is routine, expected
      invalid input on a fully public endpoint.
    - **Test suite**: `CurlyBraceFormatterTest` (10 cases covering brace nesting, already-multiline
      selector lists, comments, string-literal protection, the ASI-safety guarantee, and
      never-throws-on-unterminated-input) plus one JUnit 5 class per new operation
      (`CssOperationTest`/`LessOperationTest`/`ScssOperationTest`/`JsOperationTest`/
      `ErbOperationTest`/`XmlOperationTest`, 3–7 cases each — no Mockito, same convention as every
      existing operation test), plus 6 new `DevUtilsServiceApplicationTests` cases confirming each
      new endpoint is reachable with no `Authorization` header and that malformed XML returns `400`
      with `DEVUTILS_003` through the shared `GlobalExceptionHandler`. 66 tests total in this
      module, verified via a real `mvn -pl dev-utils-service -am test` run (JDK 21).
    - Backend-only pass, per request scope, at the time — the `gui`'s `/dev-utils` page wasn't
      wired up to these 6 new endpoints yet. **Superseded by the follow-up directly below**, done
      in a separate request right after.
    - **Follow-up: `gui`'s `/dev-utils` page wired up to all 6 new endpoints, per request.**
      `api/devUtilsApi.ts` gained one method per new operation
      (`beautifyCss`/`beautifyLess`/`beautifyScss`/`beautifyJs`/`beautifyErb`/`beautifyXml`), each
      a thin pass-through to its own endpoint — identical shape to the existing `beautifyHtml`.
      `pages/DevUtilsPage.tsx`'s `TabKey`/`TAB_KEYS` gained the 6 new hash-routable keys
      (`css-beautify`/`less-beautify`/`scss-beautify`/`js-beautify`/`erb-beautify`/
      `xml-beautify`), and its `operations` array gained one `OperationConfig` per operation — all
      under the existing `'Formatters'` category (right after `html-beautify`, before the
      `'Converters'` group), each with its own icon (`CssIcon`/`StyleIcon` (LESS)/`ColorLensIcon`
      (SCSS, evoking Sass's own brand color)/`JavascriptIcon`/`IntegrationInstructionsIcon`
      (ERB)/`AccountTreeIcon` (XML, fitting its nested-tree structure) — every icon confirmed
      present in the installed `@mui/icons-material` version before use, per this file's own
      standing reminder) and a themed "Vui Coding" placeholder matching the existing four
      operations' own convention (a CSS/LESS/SCSS snippet styling a `.card`, a JS function
      describing the project, an ERB template rendering it, an XML document describing it).
      - **`OperationConfig.inputFormat`/`DevUtilToolPanelProps.inputFormat` both widened** from
        `'json' | 'yaml' | 'html'` to add `'css' | 'less' | 'scss' | 'js' | 'erb' | 'xml'` — only
        `'json'` is ever actually branched on (it picks `buildDevUtilError`'s client-side
        `JSON.parse` fast path), so this is mostly self-documentation: `'css'`/`'less'`/`'scss'`/
        `'js'`/`'erb'` can never actually fail a submit at all (their backend operations never
        throw — see `dev-utils-service/CLAUDE.md`'s own note), so the fallback path they'd
        otherwise take is dead code in practice for those five; `'xml'` is the one that can
        genuinely reach it, taking the same `simplifyBackendMessage` fallback `'yaml'` already did.
      - **`DevUtilToolPanel.tsx`'s `OUTPUT_LANGUAGE_LABELS`/`OUTPUT_LANGUAGE_COLORS` gained one
        entry per new Prism language id** (`css`/`less`/`scss`/`javascript`/`erb`/`xml` — `xml` is
        deliberately its own key, not folded into the existing `markup` one, even though Prism's
        own `markup` grammar registers `'xml'` as an alias for highlighting purposes — keeping it
        distinct here is what stops an XML result's info-row badge from misleadingly reading
        "HTML"). Colors chosen per the same "common language-badge hue" convention the existing
        three already established: CSS blue, LESS indigo, SCSS pink (Sass's own brand color), JS
        yellow, ERB Ruby-red, XML teal.
      - Confirmed all 6 new Prism grammars (`css`/`less`/`scss`/`javascript`/`erb`, plus `xml` via
        `markup`'s own alias) are present in the installed `refractor`/`react-syntax-highlighter`
        language bundle before relying on them — the existing `Prism as SyntaxHighlighter` import
        (not the lighter `PrismLight` variant) bundles every language automatically, no manual
        `registerLanguage` call needed, same as the four pre-existing operations already rely on.
      - Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
        sandbox, so the actual on-screen result (all 6 new sidebar entries, their placeholders,
        submit/copy/download, and the info-row badge colors) is unverified in a real browser.
    - **Follow-up: 3 more backend operations, per request — JSON↔CSV conversion and a SQL
      Formatter.** New endpoints, all under the existing `/api/v1/dev-utils/**` prefix (no
      `gateway` change needed, same reasoning the earlier 6-operation follow-up already
      established): `POST /api/v1/dev-utils/json-to-csv` (`JsonToCsvOperation`, `TextRequest` — no
      minify, CSV has no distinct "compact" form), `POST /api/v1/dev-utils/csv-to-json`
      (`CsvToJsonOperation`, `MinifiableTextRequest`), `POST /api/v1/dev-utils/sql/format`
      (`SqlFormatOperation`, `MinifiableTextRequest`).
      - **New Maven dependency `com.fasterxml.jackson.dataformat:jackson-dataformat-csv`** — no
        explicit `<version>`, resolves to `2.16.1` via the same Jackson BOM
        `jackson-dataformat-yaml` already relies on (confirmed via `dependency:tree` before
        writing any code, not assumed). `JsonToCsvOperation`'s column set is the **union** of every
        row's own field names in first-seen order (not just the first row's), so a heterogeneous
        array still produces one consistent header with blank cells for whichever rows lack a
        given field; a nested object/array value is written as its own compact JSON string in the
        cell rather than flattened into further columns (CSV is inherently flat — no lossless flat
        representation exists for genuinely nested data). `CsvToJsonOperation` never infers a
        cell's type — every value comes back as a JSON string, deliberately (a ZIP code like
        `"007"` would lose its leading zero if reinterpreted as a number). New
        `DevUtilsErrorCode.INVALID_CSV` (`DEVUTILS_004`) for `CsvToJsonOperation`'s real failure
        path (backed by Jackson's own `CsvMapper`, a genuine parser); `JsonToCsvOperation` reuses
        `INVALID_JSON` instead of getting its own code, since its input is JSON either way (a
        genuine syntax error, or valid JSON in a shape that can't become rows — e.g. a bare array
        of numbers).
        - **Bug fix, found by a failing test, not anticipated up front**: Jackson's
          `MappingIterator#next()` can't declare a checked exception (it implements
          `java.util.Iterator`), so a structural CSV failure discovered mid-iteration (a row with a
          different column count than the header — the exact scenario `INVALID_CSV` exists for)
          surfaces as an *unchecked* `RuntimeJsonMappingException`, not the `IOException` a plain
          `try`-with-resources `close()` can still throw. `CsvToJsonOperation`'s original single
          `catch (IOException e)` never caught this at all, so that exact case slipped straight
          through as an uncaught runtime exception (a raw `500`) instead of the intended `400`
          with `DEVUTILS_004`. Fixed by adding a dedicated `catch (RuntimeJsonMappingException e)`
          that unwraps `getCause()` (the original `JsonMappingException`, itself a
          `JsonProcessingException`) through the same `ParsingExceptionMessages.friendlyMessage`
          path the `IOException` branch already used.
      - **`SqlFormatOperation` delegates entirely to a new `service/impl/support/SqlFormatter`** —
        a lenient, **keyword-driven** pretty-printer/minifier for SQL, not a real SQL-grammar
        parser, the same "textual reformatter" trade-off `CurlyBraceFormatter` already makes for
        CSS/LESS/SCSS/JS, for a related reason: a real SQL parser would also have to commit to one
        specific dialect (MySQL/Postgres/SQL Server/Oracle all diverge), which a general-purpose
        formatting tool has no way to know in advance. Fully re-tokenizes and rebuilds the output
        from scratch (no ASI-style hazard to preserve original whitespace against, unlike JS).
        Line breaks are keyword-triggered (`SELECT`/`FROM`/`WHERE`/`GROUP BY`/`ORDER BY`/`HAVING`/
        `LIMIT`/`OFFSET`/`INSERT INTO`/`VALUES`/`UPDATE`/`SET`/`DELETE FROM`/`UNION`/`UNION ALL`/
        every `JOIN` variant/`ON`/`AND`/`OR`), indented by live paren-nesting depth (`AND`/`OR`
        get one extra level) — a subquery's own clauses end up indented automatically, since
        indentation tracks paren depth, not which clause "owns" them. **Deliberately does not
        split a comma-separated column/value list onto separate lines** — same reasoning
        `CurlyBraceFormatter` already documents for CSS selector lists: no context-free way to
        tell a `SELECT` column list apart from a function call's argument list
        (`COUNT(a, b)`) without real parsing. **Known, deliberate trade-off: `(` never gets a
        leading space**, regardless of context — correct for a function call (`COUNT(*)`), merely
        a different style preference for something like `VALUES(1, 2, 3)` — no parser-free way to
        tell "function name" from "keyword that conventionally gets a space before its paren"
        apart, so this picks the rule that's never actually *wrong*. Quote handling is
        dialect-agnostic: `'...'`/`"..."`/`` `...` `` are all atomic tokens tolerating *either* a
        doubled quote *or* a backslash escape. Comments (`-- line`, `# line` MySQL, `/* block */`)
        are preserved verbatim by `beautify`, stripped by `minify`. Never throws — no matching
        `DevUtilsErrorCode`.
        - **Two real bugs caught by failing tests, both fixed before this landed**: (1) the
          keyword-matching branch originally emitted the *canonical uppercase* keyword text from
          its own lookup table instead of the actual input token, silently upper-casing every
          recognized keyword regardless of how the caller wrote it — every single beautify test
          with lowercase input failed on this alone. Fixed by appending the original token text,
          using the lookup table only to decide *whether* a line break applies, never what to
          render. (2) `minify` never actually stripped comment tokens — they were tokenized
          correctly but never filtered out during rendering, so a comment survived straight into
          the "minified" output. Fixed by skipping any comment-shaped token when rendering in
          single-line mode.
      - **Test suite**: `SqlFormatterTest` (10 cases) plus one JUnit 5 class per new operation
        (`JsonToCsvOperationTest`/`CsvToJsonOperationTest`/`SqlFormatOperationTest`, 3–7 cases
        each), plus 4 new `DevUtilsServiceApplicationTests` cases (reachability for all 3 new
        endpoints, plus malformed CSV returning `400` with `DEVUTILS_004`). 95 tests total in this
        module now, verified via a real `mvn -pl dev-utils-service -am test` run (JDK 21).
      - Backend-only pass, per request scope, at the time — the `gui`'s `/dev-utils` page wasn't
        wired up to these 3 new endpoints yet. **Superseded by the follow-up directly below.**
    - **Follow-up: `gui`'s `/dev-utils` page wired up to all 3 new endpoints, per request.**
      `api/devUtilsApi.ts` gained `jsonToCsv` (no `minify` field, same reasoning `jsonToYaml`
      already documents — CSV has no distinct "compact" form), `csvToJson`, `formatSql` — all thin
      pass-throughs identical in shape to the existing methods. `DevUtilsPage.tsx`'s `TabKey`/
      `TAB_KEYS` gained `json-to-csv`/`csv-to-json`/`sql-format`, and its `operations` array gained
      one `OperationConfig` each — `json-to-csv`/`csv-to-json` inserted into the existing
      `'Converters'` group (right after `json-to-yaml`, alongside `yaml-to-json`), `sql-format`
      appended to the `'Formatters'` group (right after `xml-beautify`) — grouped by the same
      "which backend concern does this front" convention every other operation here already
      follows. Reused the sidebar's existing consolidated icons rather than adding new ones
      (`SwapHorizIcon` for both CSV converters, matching the existing YAML/JSON converters;
      `AutoFixHighIcon` for `sql-format`, matching every other Formatters-group entry) — the
      per-operation icon variety from the original 6-operation pass had since been deliberately
      simplified down to a handful of reused icons.
      - **`OperationConfig.inputFormat`/`DevUtilToolPanelProps.inputFormat` widened** to add
        `'csv' | 'sql'`. `json-to-csv` itself stays `'json'`, not a new value — its backend
        operation reuses `INVALID_JSON` for its own failures (see `dev-utils-service/CLAUDE.md`),
        so it correctly takes the same client-side `JSON.parse` fast path `json-format`/
        `json-to-yaml` already get. `csv-to-json` is the third operation (after `yaml`/`xml`) with
        a real backend invalid-input error path (`CsvToJsonOperation`'s own Jackson `CsvMapper`),
        so it takes the same `simplifyBackendMessage` fallback those two already use — no
        client-side CSV parser exists to give it a faster path the way JSON has one.
        `sql-format`'s backend operation never throws at all (`SqlFormatter` is lenient, same as
        `CurlyBraceFormatter`), so `'sql'` is inert the same way `'css'`/`'less'`/etc. already are.
        `errorFormatting.ts`'s own doc comment updated to describe all three accurately.
      - **`DevUtilToolPanel.tsx`'s `OUTPUT_LANGUAGE_LABELS`/`OUTPUT_LANGUAGE_COLORS` gained `csv`/
        `sql` entries** (green for CSV, evoking a spreadsheet; amber for SQL) — both Prism/refractor
        grammars confirmed present in the installed bundle first, same verification step the
        earlier 6-operation pass already established.
      - Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
        sandbox, so the actual on-screen result (all 3 new sidebar entries, their placeholders,
        submit/copy/download, and the info-row badge colors) is unverified in a real browser.
    - **Follow-up: 3 more backend operations, per request — bidirectional PHP↔JSON conversion and
      a String Case Converter.** New endpoints, all under the existing `/api/v1/dev-utils/**`
      prefix (no `gateway` change needed, same reasoning every prior follow-up in this entry
      already established): `POST /api/v1/dev-utils/php-to-json` (`PhpToJsonOperation`,
      `MinifiableTextRequest`), `POST /api/v1/dev-utils/json-to-php` (`JsonToPhpOperation`,
      `MinifiableTextRequest`), `POST /api/v1/dev-utils/string-case/convert`
      (`StringCaseOperation`, `TextRequest` in, a new `StringCaseResponse` out).
      - **PHP↔JSON is backed by two new, self-contained utilities — no third-party PHP parsing
        library** — `service.impl.support.PhpArrayParser` (PHP→value tree) and
        `service.impl.support.PhpArrayWriter` (value tree→PHP). `PhpArrayParser` is a **real,
        validating recursive-descent parser** (the same "real parse, real invalid-input error"
        shape `XmlOperation`/`CsvToJsonOperation` already establish), not a lenient reformatter —
        it has to fully understand the value structure to convert it. Supports bracket (`[...]`)
        and legacy `array(...)` syntax; single-quoted strings honor only `\'`/`\\` as real escapes
        (PHP's own rule), double-quoted strings honor `\n`/`\t`/`\r`/`\"`/`\\`/`\$` (deliberately
        *not* evaluating variable interpolation like `"$name"` — a literal text converter, not a
        PHP interpreter); `//`/`#`/block comments are skipped anywhere between tokens.
        **Tolerates a full PHP snippet, not just the bare array literal** — an optional leading
        `<?php` tag, an optional `return` keyword, and an optional trailing `;`/`?>` are all
        skipped if present, so `JsonToPhpOperation`'s own output can be fed straight back into the
        parser unmodified (verified by a real round-trip test). An array with no explicit `=>`
        keys, or whose explicit keys form the exact sequence `0, 1, 2, ...` (PHP's own
        auto-increment keys, e.g. from a `var_export()` dump), becomes a JSON array; any other
        array becomes a JSON object with every key stringified. New
        `DevUtilsErrorCode.INVALID_PHP` (`DEVUTILS_005`) for `PhpToJsonOperation`'s own real
        failure path (`PhpArrayParser.PhpParseException`'s message already carries a
        `"(line N, column M)"` location, the same convention `ParsingExceptionMessages`/
        `XmlOperation` already establish); `JsonToPhpOperation` reuses `INVALID_JSON` instead —
        any valid JSON value can always become a PHP array/scalar, so the only possible failure is
        a genuine JSON syntax error. Both operations get a real minify choice too (unlike
        `JsonToCsvOperation`'s own CSV-has-no-compact-form precedent) — a PHP array literal has
        just as meaningful a single-line form as JSON does; `JsonToPhpOperation`'s minify mode
        collapses to one line with no space around `=>`/after a comma, the same "minimal necessary
        whitespace" style `JsonFormatOperation`'s own compact writer already uses.
      - **`StringCaseOperation` is the one operation in this batch whose output is genuinely
        richer than a single string** — new `dto.StringCaseResponse` (camelCase/pascalCase/
        snakeCase/kebabCase/constantCase/titleCase/sentenceCase, all at once), exactly the
        scenario `DevUtilResponse`'s own Javadoc anticipated for a future operation like this.
        Reuses `TextRequest` (no minify — there's no "compact form" of a case conversion).
        Delegates to a new `service.impl.support.StringCaseConverter` — splits input into words
        using the standard two-part heuristic most case-conversion tools use (a boundary between a
        lowercase-or-digit and a following uppercase letter, and between the last letter of an
        uppercase run and a following capitalized word, e.g. `XMLHttpRequest` → `XML`, `Http`,
        `Request`), so both delimiter-separated input and already-cased input (camelCase/
        snake_case/etc.) split correctly — verified by a round-trip test confirming every case
        variant re-splits back into the same words. A pure text transform with no notion of
        "invalid" input — never throws, no matching `DevUtilsErrorCode`.
      - **Test suite**: `PhpArrayParserTest` (14 cases — associative vs. list detection including
        the sequential-explicit-keys case, legacy `array(...)` syntax, single- vs. double-quoted
        escape rules, comment skipping, trailing commas, real `PhpParseException` line/column
        assertions), `PhpArrayWriterTest` (6 cases, including a real round-trip through
        `PhpArrayParser`), `StringCaseConverterTest` (7 cases, including a round-trip confirming
        every variant re-splits into the same words), plus one JUnit 5 class per new operation
        (`PhpToJsonOperationTest`/`JsonToPhpOperationTest`/`StringCaseOperationTest`), plus 5 new
        `DevUtilsServiceApplicationTests` cases (reachability for all 3 new endpoints, plus
        malformed PHP returning `400` with `DEVUTILS_005`). 133 tests total in this module now,
        verified via a real `mvn -pl dev-utils-service -am test` run (JDK 21).
      - Backend-only pass, per request scope, at the time — the `gui`'s `/dev-utils` page wasn't
        wired up to these 3 new endpoints yet. **Superseded by the follow-up directly below.**
    - **Follow-up: `gui`'s `/dev-utils` page wired up to all 3 new endpoints, per request.**
      `api/devUtilsApi.ts` gained `phpToJson`/`jsonToPhp` (thin pass-throughs, identical in shape
      to the existing methods) and `convertStringCase` — the first method in this file to return
      something other than `DevUtilsResponse` (`Promise<StringCaseResponse>`, a new type mirroring
      the backend's own record field-for-field). `DevUtilsPage.tsx`'s `TabKey`/`TAB_KEYS` gained
      `php-to-json`/`json-to-php`/`string-case-convert`, and its `operations` array gained one
      `OperationConfig` each — `php-to-json`/`json-to-php` joined the existing `'Converters'`
      group (right after `csv-to-json`), each with a genuine `PhpOutlined` icon (MUI's own PHP
      logo glyph, not a reused generic one, per request); `string-case-convert` got its own new
      `'Text Tools'` category (appended at the end) rather than being stretched to fit
      `'Formatters'`/`'Converters'`, with a dedicated `AbcOutlined` icon.
      - **`string-case-convert` doesn't fit `DevUtilToolPanel`'s single-string output shape at
        all** — `StringCaseOperation`'s own response has 7 named fields, not one string. Rather
        than building a second, parallel result-rendering component just for this one operation,
        its `onSubmit` (a new module-level `formatStringCaseResult` helper) formats the 7 variants
        into that same `{ output: string }` shape — one `"<Label>\n<value>"` pair per variant,
        blank-line separated (the same plain-text layout the original request itself was written
        in) — reusing the entire existing Input/Output panel for free instead of a bespoke
        multi-value UI. `outputLanguage: 'text'` has no real Prism grammar to highlight against
        (deliberately), so it renders unstyled; `OUTPUT_LANGUAGE_LABELS`/`OUTPUT_LANGUAGE_COLORS`
        still gained a real `text` entry (neutral grey) so its info-row badge shows something
        meaningful rather than the raw string `"text"`.
      - **`OperationConfig.inputFormat`/`DevUtilToolPanelProps.inputFormat` widened** to add
        `'php' | 'text'`. `json-to-php` stays `'json'`, not a new value — same reasoning
        `json-to-csv` already established (its backend reuses `INVALID_JSON`). `php-to-json` is
        the fourth operation (after `yaml`/`xml`/`csv`) with a real backend invalid-input error
        path, so it takes the same `simplifyBackendMessage` fallback those three already use.
        `string-case-convert`'s backend operation never throws at all, so `'text'` is inert the
        same way `'css'`/`'less'`/etc. already are. `errorFormatting.ts`'s own doc comment updated
        to describe all of this accurately.
      - **`DevUtilToolPanel.tsx`'s `OUTPUT_LANGUAGE_LABELS`/`OUTPUT_LANGUAGE_COLORS` gained `php`/
        `text` entries** (PHP's own brand indigo; neutral grey for `text`) — the `php` Prism/
        refractor grammar confirmed present in the installed bundle first, same verification step
        every earlier operation pass already established.
      - Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
        sandbox, so the actual on-screen result (all 3 new sidebar entries, their placeholders,
        submit/copy/download, the formatted case-variant output block, and the info-row badge
        colors) is unverified in a real browser.
    - **Follow-up: operations grouped by a new `OperationGroup` (backend) / `group` field (`gui`),
      per request — "Group all the existing Operation in a group calls 'FORMATTERS' since we will
      implement new operations belongs to another group (ENCODERS/DECODERS, INSPECTORS, WEB,
      GENERATORS) next."** Backend first, then the GUI, per the request's own explicit ordering.
      - **Backend**: `service.DevUtilOperation` gained one real abstract method,
        `group(): OperationGroup` — the one deliberate exception to this interface's own "no shared
        method" marker-interface philosophy (see that class's own updated Javadoc for why this one
        doesn't reopen the "forced shared `execute()` shape" problem the marker-interface design
        exists to avoid). New `service.OperationGroup` enum — `FORMATTERS`/`ENCODERS_DECODERS`/
        `INSPECTORS`/`WEB`/`GENERATORS`, each with a Title-Case `getLabel()` (the `gui` applies its
        own CSS `text-transform`, matching the existing "Tools" sidebar caption convention). All 16
        existing operations declare `FORMATTERS`; the other 4 groups are declared ahead of use, each
        with a concrete future-operation example in the enum's own Javadoc, so the `gui`'s
        group-by-label sidebar rendering already has a complete, stable section order today.
        `group()` is abstract, not `default`, so a future non-`FORMATTERS` operation that forgets to
        override it fails to compile rather than silently landing in the wrong sidebar section.
        Verified via a full `mvn -pl dev-utils-service -am test` run (JDK 21) — 161/161 tests still
        passing, 0 failures (a compile-time-only addition, nothing to assert differently).
      - **`gui`**: `config/operations.tsx`'s `OperationConfig` gained a `group: OperationGroupName`
        field (a new literal-union type mirroring the backend enum's own values, plus an
        `OPERATION_GROUP_ORDER` constant mirroring its declaration order) — deliberately distinct
        from the interface's existing, finer-grained `category` field (`'Formatters'`/
        `'Converters'`/`'Text Tools'`), which stays as a per-operation eyebrow label within a group,
        not replaced by it. All 16 `OPERATIONS` entries set `group: 'Formatters'`.
        `pages/DevUtilsPage.tsx`'s sidebar `<List>` now buckets the (possibly search-filtered)
        operation list by `OPERATION_GROUP_ORDER` via a new `groupedVisibleOperations` derivation
        (a group with zero matching operations is dropped, not rendered as an empty headline),
        rendering an uppercase section caption (`variant="caption" fontWeight={700}
        sx={{textTransform:'uppercase', letterSpacing:0.5}}` — same styling the existing "Tools"
        caption above the search box already establishes, hidden when the sidebar is collapsed)
        above each group's own operations. Built generically off the fixed group order, not
        hardcoded to a single "FORMATTERS" string, so the first `ENCODERS_DECODERS`/`INSPECTORS`/
        `WEB`/`GENERATORS` operation gets its own sidebar section for free with no further page
        change needed. Verified via a clean `tsc --noEmit` and a successful `vite build` only — no
        Docker in this sandbox, so the actual on-screen section headline is unverified in a real
        browser.
    - **Follow-up: the Input/Output panels made viewport-relative, then partially reverted for
      Output, then two real regressions found and fixed — summarized here as the current end
      state (see `gui/CLAUDE.md`'s own dev-utils section for the full per-step detail).**
      `pages/DevUtilsPage.tsx` computes a live `panelHeight` (viewport height minus the tool
      panel's own top position, floored at `PANEL_MIN_HEIGHT`) and a live `sidebarHeight`
      ("headline card height + gap + panelHeight," derived from the same measurements, **not**
      measured off the main column's own rendered DOM height). Input pins to `panelHeight`
      exactly (`height: availableHeight`); the sidebar pins to `sidebarHeight` exactly
      (`height`, not `maxHeight`). **Output deliberately does not pin to it** — per a follow-up
      request ("Allow the Output height grow to maximum 1000 line number. Above that -> user have
      to scroll"), it grows with its own content instead, floored (not capped) at `availableHeight`
      via `minHeight`, and capped by a new line-count-based `OUTPUT_MAX_HEIGHT` (1000 lines ×
      20px/line) before scrolling internally.
      - **Regression #1** (reported: "the height of the Sidebar now not equals to the height of
        the Input/Output"): the sidebar used `maxHeight`, which only clamps the upper bound — a
        short tool list rendered shorter than the main column instead of matching it. Fixed by
        switching to a fixed `height`.
      - **Regression #2** (reported: "when the height of the Output grow based on the content, the
        Height of the Sidebar is growing too which is not correct"): `sidebarHeight` used to be a
        `ResizeObserver` measurement of the whole main column's real DOM height, which picked up
        Output's own content-driven growth and inflated the sidebar along with it. Fixed by
        computing `sidebarHeight` directly from viewport measurements instead (alongside
        `panelHeight`, in the same effect), independent of Output's own rendered size entirely.
      - Verified via a clean `tsc --noEmit` and a successful `vite build` only, at every step — no
        Docker in this sandbox, so none of this is exercised in a real browser.
    - **Follow-up: a resizable divider between Input/Output, per request ("Move to #2" — the
      second of the ideas discussed for a large payload; #1, the viewport-relative height work
      above, was accepted first).** `DevUtilToolPanel.tsx` gained a hand-rolled drag handle rather
      than reusing `@tasks/components/ResizeHandle.tsx`'s `react-resizable-panels`-based one — that
      library's own `Group` container defaults to `height: '100%'`/`overflow: 'hidden'` (confirmed
      by reading the installed package's own compiled source, not assumed), which assumes a
      bounded, already-known-height parent — fundamentally incompatible with Output's own "can grow
      past the viewport for a long response, lets the page scroll" design from the two regressions
      just fixed above; wrapping this row in a `Group` risked reopening one of them. Instead: a new
      `splitPercent` state (persisted to `localStorage`, same standing-preference treatment the
      sidebar's own collapse state gets) drives each `Paper`'s own `flex-basis` (`calc(P% -
      20px)`/`calc((100-P)% - 20px)`, the 20px compensating for the handle's own 8px width plus the
      row's two 16px `gap`s either side of it — the same `calc()`-gap-compensation technique this
      codebase already establishes for a percentage split sharing a row with a `gap`); a small
      styled `Box` between the two Papers (`role="separator"`, drag via Pointer Events with
      `setPointerCapture` so `onPointerMove`/`onPointerUp` stay plain props with no window-level
      listener to clean up, double-click to reset to 50/50, arrow-key nudging for keyboard
      accessibility) drives it. Hidden below the `md` breakpoint, where this row wraps Input/Output
      onto separate full-width lines and a horizontal drag handle wouldn't mean anything. Neither
      side's own `height`/`minHeight` is affected by dragging — only the width split changes.
      Verified via a clean `tsc --noEmit` and a successful `vite build` only — no Docker in this
      sandbox, so the actual drag/keyboard/double-click/persistence behavior is unverified in a
      real browser.
    - **Follow-up: the always-visible handle/gap above replaced with a zero-gap, hover-only reveal,
      per request** — "remove the gap between Input and Output so that user can directly hold the
      Input border right/Output border left," after discussing the tradeoff (a bare 1px border is
      a poor drag target on its own, the same reasoning `react-resizable-panels`' own
      `resizeTargetMinimumSize` docs make) and landing on a hybrid: the row's own `gap` is gone
      entirely (the two `Paper`s' `flex-basis` percentages now sum to 100% directly, no
      calc()-overhead subtraction needed), and the handle is now a `position: 'absolute'` overlay
      (`left: ${splitPercent}%` against the row's own `position: 'relative'`, `top: 0, bottom: 0`
      to stretch across the row's own resolved height regardless of it being auto-sized) rather
      than a flex item of its own — a comfortable 16px hit target/`cursor: 'col-resize'` zone that
      renders **no visible line at rest at all** (the two Papers' own adjacent borders already read
      as one seam), fading a highlighted line in only on hover/focus/drag via `opacity`, not a
      width change (there's nothing to widen from at rest). Verified via a clean `tsc --noEmit` and
      a successful `vite build` only — no Docker in this sandbox, so the actual zero-gap look and
      the hover-reveal are unverified in a real browser.
    - **Follow-up: both Input and Output rebuilt on real CodeMirror 6 editors
      (`@uiw/react-codemirror`), replacing the plain `TextField`/read-only `react-syntax-highlighter`
      pairing, per request ("Move to #3" — the last of the three ideas from the original design
      discussion). Two decisions confirmed with the user first, since both were named as open forks
      in that same discussion: CodeMirror over Monaco (far lighter, no web worker/CDN story to
      manage — this app's bundle was already flagged for size), and both panels rebuilt, not just
      Output (so Input also gets real syntax highlighting for whatever it's typing/pasting).**
      New `config/codeMirrorConfig.ts` — `getCodeMirrorExtensions(languageId)` maps every language
      id this feature's own operations pass (both `inputFormat`'s and `outputLanguages.ts`'s
      slightly different key sets for the same two languages) to the matching CodeMirror language
      package (`@codemirror/lang-{json,yaml,html,css,less,sass,javascript,xml,sql,php}`, installed
      new) — `erb`/`csv`/`text` fall back to plain, unhighlighted text (no maintained CodeMirror 6
      ERB grammar exists, and CSV/plain text aren't real "languages" to highlight in the first
      place). `editorChromeTheme` (a small shared `EditorView.theme()`) restores the panel's
      existing `16px` content padding/`0.8rem` font size, both noticeably smaller under CodeMirror's
      own defaults. Output uses the new `@uiw/codemirror-theme-vscode` package's `vscodeDark` theme
      (a real VS Code Dark+ port) in place of the old hand-tuned `vscDarkPlus`+`!important`
      line-number-color override; Input uses CodeMirror's own plain `'light'` theme, matching the
      app's own chrome the same way the old `TextField` did.
      `react-syntax-highlighter` itself is **not** removed as a dependency — `@chat/
      components/MarkdownRenderer.tsx` and `@content/components/MarkdownField.tsx` both still use
      it; only this one file stopped. Both editors' fixed-height/floor+cap sizing (`availableHeight`
      for Input, the floor-plus-`OUTPUT_MAX_HEIGHT`-cap for Output) carried over unchanged, mapped
      onto CodeMirror's own `height`/`maxHeight` props and `style={{flex:1, minHeight:0}}` instead
      of the old hand-rolled `TextField`/`react-syntax-highlighter` CSS overrides — see
      `gui/CLAUDE.md`'s own dev-utils section for the full reasoning behind each. Bundle impact:
      production build grew from ~2.28 MB/725 KB gzip to ~3.04 MB/986 KB gzip (11 new dependencies:
      the editor + its VS Code theme + 10 language packages) — a real, expected cost of this
      request, not a regression to chase down. Verified via a clean `tsc --noEmit` and a successful
      `vite build` only — no Docker in this sandbox, so the actual editors (typing/highlighting in
      Input, read-only display/scroll in Output, both panels' sizing) are unverified in a real
      browser.
    - **3 bugs reported directly right after the CodeMirror follow-up above landed, all fixed in
      `config/codeMirrorConfig.ts`/`DevUtilToolPanel.tsx`.** (1) A dotted focus outline appearing
      when editing Input — `@codemirror/view`'s own base theme draws `&.cm-focused { outline: '1px
      dotted #212121' }` on `.cm-editor` by design (to cover the gutters, which a plain native
      focus ring on the content-editable element alone wouldn't); removed via `'&.cm-focused':
      { outline: 'none' }` on the shared `editorChromeTheme`, since both editors already sit inside
      their own bordered `Paper` card. (2) No scrollbar for content overflowing Input's own
      width/height — CodeMirror's `height="100%"` prop needs its direct parent to have a genuinely
      definite height for the percentage to resolve, and relying on ambient flex stretch/grow
      through the wrapping `Box`/Paper chain didn't reliably produce one in practice (the editor
      fell back to auto-sizing to content, clipped by the Paper's own `overflow: 'hidden'` instead
      of ever engaging its own internal scroller). Fixed by switching Input's editor to `position:
      'absolute', inset: 0` against a `position: 'relative'` wrapping `Box` — the same "fill an
      already-laid-out ancestor directly, no percentage-resolution to fail" technique already used
      for the resize handle elsewhere in this file. (3) Output's own min-height no longer matching
      Input/the sidebar (a real regression) — the first cut inferred the floor from an *ambient*
      `flex: 1` fill rather than setting it directly, which didn't actually land. Fixed with an
      explicit, measured `minHeight` prop straight on the Output editor: a new `useLayoutEffect`
      measurement of the header+info row's own real rendered height feeds
      `minHeight={`${availableHeight - outputChromeHeight}px`}`, deterministic rather than inferred
      from flex-grow distribution. Verified via a clean `tsc --noEmit` and a successful
      `vite build` only — no Docker in this sandbox, so none of the three actual fixes is verified
      in a real browser.
    - **Follow-up: a "maximize this panel" toggle, per request** — the last of the original design
      discussion's ideas still unbuilt. A header `IconButton` on each panel (`OpenInFullIcon`/
      `CloseFullscreenIcon`) toggles a new `maximizedPanel: 'input' | 'output' | null` state: the
      maximized side's `Paper` takes the full row width (`flex-basis: 100%`, ignoring
      `splitPercent`), the other side hides via `display: 'none'` (kept mounted, not conditionally
      rendered, so its CodeMirror instance doesn't lose cursor/scroll/undo state), and the resize
      handle hides too (nothing to drag with one side gone). Deliberately plain component state,
      not persisted to `localStorage` like `splitPercent` — a momentary focus mode, resetting to
      the normal split view on every tool switch. Width-only, not height too — Output's own height
      already grows independently of Input via its existing floor/cap design, so maximizing didn't
      need a separate height mechanism on top of that. Verified via a clean `tsc --noEmit` and a
      successful `vite build` only — no Docker in this sandbox, so the actual maximize/restore
      toggle is unverified in a real browser.
    - **Follow-up: 2 new operations, `Base64EncodeOperation`/`Base64DecodeOperation`, per request
      ("add new operation in group ENCODERS_DECODERS: Base64 String - Encode and decode Base64
      strings") — the first operations to declare `OperationGroup.ENCODERS_DECODERS` instead of
      `FORMATTERS`.** Backs `POST /api/v1/dev-utils/base64/{encode,decode}`; both use
      `java.util.Base64`'s standard (not URL-safe) alphabet and UTF-8 byte encoding explicitly.
      `Base64EncodeOperation` never throws (every string has a valid encoding); new
      `DevUtilsErrorCode.INVALID_BASE64` (`DEVUTILS_006`) backs `Base64DecodeOperation`'s own real
      failure path (`java.util.Base64.Decoder`, not the lenient MIME decoder — see
      `dev-utils-service/CLAUDE.md`'s own note for the full reasoning), input `String#strip()`ped
      first to tolerate a pasted string's leading/trailing newline. Both verified via a real
      standalone Java harness first (originally against a Vietnamese-diacritics example, later
      switched to an English sentence plus an emoji per a direct follow-up request — still
      genuinely multi-byte UTF-8 via the emoji alone), which caught the harness itself mis-encoding
      on Windows due to `javac`'s own platform-default charset (not a bug in the operation code —
      fixed by compiling/running with explicit UTF-8 encoding).
      - **`gui`**: the first operation needing two independent action buttons over the same input
        (Encode/Decode) instead of one — `OperationConfig`/`DevUtilToolPanelProps` both gained an
        optional `secondaryAction: { label, onSubmit }`, rendered as a second `SubmitButton`; a new
        `savingAction: 'primary' | 'secondary' | null` (replacing the old plain `saving` boolean)
        disables both buttons while either is submitting, preventing an overlapping double-submit.
        `config/operations.tsx` gained the new `base64-string` entry (group/category
        `'Encoders/Decoders'`, the sidebar's first section besides "Formatters").
      - 11 new backend tests (161→172): `Base64EncodeOperationTest`/`Base64DecodeOperationTest`
        (plain ASCII, the exact reported multi-byte example each direction, edge cases, and the
        malformed-input `INVALID_BASE64` case) plus 3 new `DevUtilsServiceApplicationTests` cases.
        Verified via a real `mvn -pl dev-utils-service -am test` run (JDK 21) and a clean
        `tsc --noEmit`/successful `vite build` on the GUI side — no Docker in this sandbox, so the
        actual two-button GUI flow is unverified in a real browser.
    - **Follow-up: 2 more operations, `UrlEncodeOperation`/`UrlDecodeOperation`, per request ("add
      new operation in ENCODERS_DECODERS group: URL Encode / Decode") — the second pair to declare
      `OperationGroup.ENCODERS_DECODERS`, same Encode/Decode two-button shape `base64-string`
      already established.** Backs `POST /api/v1/dev-utils/url/{encode,decode}`. `UrlEncodeOperation`
      delegates to `URLEncoder.encode(input, UTF_8)` with its one divergence from conventional URL
      percent-encoding corrected (`+` → `%20` for a space, matching `encodeURIComponent`); never
      throws, same as `Base64EncodeOperation`. New `DevUtilsErrorCode.INVALID_URL_ENCODING`
      (`DEVUTILS_007`) backs `UrlDecodeOperation`'s own real failure path
      (`URLDecoder.decode(input, UTF_8)`'s `IllegalArgumentException`). `gui`'s
      `config/operations.tsx` gained the `url-string` entry (reusing the existing
      `secondaryAction`/`savingAction` mechanism `base64-string` already introduced — no new GUI
      capability needed this time); `api/devUtilsApi.ts` gained `encodeUrl`/`decodeUrl`. 10 new
      backend tests (172→182 — `UrlEncodeOperationTest`/`UrlDecodeOperationTest` plus 3 new
      `DevUtilsServiceApplicationTests` cases), plus a clean `tsc --noEmit`/successful `vite build`
      on the GUI side — no Docker in this sandbox, so the actual two-button GUI flow is unverified
      in a real browser.
    - **Follow-up: 2 more operations, `HtmlEntityEncodeOperation`/`HtmlEntityDecodeOperation`, per
      request ("HTML Entity - HTML entity encoding and decoding") — the third pair to declare
      `OperationGroup.ENCODERS_DECODERS`.** Backs `POST /api/v1/dev-utils/html-entity/{encode,
      decode}`. Encode escapes only the five structurally-significant markup characters (`& < >
      " '`) into `&amp; &lt; &gt; &quot; &#39;`, character by character — deliberately not a full
      ISO-8859-1/HTML4 named-entity table (which would also rewrite `©` to `&copy;`, contradicting
      the reported example where `©` survives untouched); never throws. Decode is the inverse of
      that same fixed set (plus `&apos;`/`&#x27;` tolerated as alternate apostrophe spellings) via
      one single-pass regex replace — never throws either, and leaves any unrecognized `&...;`
      sequence (`&copy;`, `&nbsp;`) completely untouched rather than guessing, the same "no
      invalid-input concept" shape as encode. This is the one operation pair in the module where
      *neither* direction has a matching `DevUtilsErrorCode`. `gui`'s `config/operations.tsx`
      gained the `html-entity-string` entry (reusing the existing `secondaryAction`/`savingAction`
      mechanism, no new GUI capability needed); `api/devUtilsApi.ts` gained
      `encodeHtmlEntity`/`decodeHtmlEntity`. 11 new backend tests (182→193 —
      `HtmlEntityEncodeOperationTest`/`HtmlEntityDecodeOperationTest` plus 2 new
      `DevUtilsServiceApplicationTests` cases), plus a clean `tsc --noEmit`/successful `vite build`
      on the GUI side — no Docker in this sandbox, so the actual two-button GUI flow is unverified
      in a real browser.
    - **Follow-up: 1 new operation, `HashGeneratorOperation`, per request ("Hash Generator -
      Genereate SHA-1, SHA-256, SHA-384, SHA-512") — the first operation to declare
      `OperationGroup.INSPECTORS`.** Backs `POST /api/v1/dev-utils/hash/generate`; computes all
      four digests at once over the input's raw UTF-8 bytes (`MessageDigest`, hex-encoded
      lowercase via `HexFormat.of()`) into a new `dto.HashResponse` — the second operation (after
      String Case Converter) whose output is genuinely richer than a single string. Never throws
      (all four algorithms are guaranteed present on every JDK), so it has no matching
      `DevUtilsErrorCode`. `gui`'s `config/operations.tsx` gained the `hash-generator` entry —
      the first entry outside "Formatters"/"Encoders/Decoders," rendering the sidebar's
      "Inspectors" section headline for the first time — reusing `formatStringCaseResult`'s exact
      formatting trick via a new `formatHashResult` helper; `api/devUtilsApi.ts` gained
      `generateHash`; `types.ts` gained a matching `HashResponse` interface. 5 new backend tests
      (193→198 — `HashGeneratorOperationTest` plus 1 new `DevUtilsServiceApplicationTests` case),
      verified against a real standalone Java harness for the exact reported example (a
      genuinely multi-byte UTF-8 input, via the em dash) before writing the test assertion, plus a
      clean `tsc --noEmit`/successful `vite build` on the GUI side — no Docker in this sandbox, so
      the actual GUI flow is unverified in a real browser.
    - **Follow-up: a genuinely bespoke Input/Output layout for this one operation, per direct
      request — reverted once (a first attempt added an opt-in `multiValueOutput` flag directly
      to the shared `DevUtilToolPanel.tsx`; rejected specifically for touching that shared
      component) and rebuilt as a wholly separate file instead.** New `gui`
      `components/HashGeneratorPanel.tsx` — `DevUtilToolPanel.tsx` itself is untouched;
      `DevUtilsPage.tsx` picks between the two components with a direct
      `activeOperation.key === 'hash-generator'` check, reusing the page's existing lifted
      `input`/`output` state (so Sample/Clear keep working) but rendering a much simpler,
      dedicated layout: a plain multiline `TextField` (no CodeMirror), and each of the four
      digests as its own bordered card with a fixed **white** (`#ffffff`) background, a headline
      (algorithm name), and its own Copy button with independent "Copied!" feedback — the actual
      problem a single concatenated block made hard to copy from. A local `parseHashLines` helper
      reverses `formatHashResult`'s own formatting convention back into the four pairs; card text
      uses fixed `grey.900`/`grey.800` literals rather than the theme's `text.primary` token, since
      that token would go nearly invisible against a background pinned to white regardless of the
      app's dark mode. Verified via a clean `tsc --noEmit`/successful `vite build` only — no
      Docker in this sandbox, so the actual layout is unverified in a real browser.
    - **Two follow-up fixes to `HashGeneratorPanel.tsx`, both reported directly**: (1) the Input
      `TextField`'s own default outlined border, nested inside the card's `Paper` border, read as
      a "box inside a box" — hidden via a `.MuiOutlinedInput-notchedOutline` override across all
      three states (default/hover/focus). (2) each card's headline was a uniform `grey.900`
      regardless of algorithm — new `HASH_LABEL_COLORS` gives each of the four a distinct fixed
      color, the same per-type badge convention `outputLanguages.ts#OUTPUT_LANGUAGE_INFO` already
      establishes elsewhere in this feature. Verified via a clean `tsc --noEmit`/successful
      `vite build` only — no Docker in this sandbox, so both fixes are unverified in a real browser.
    - **Follow-up: 2 more operations, `PhpSerializeOperation`/`PhpUnserializeOperation`, per
      request ("PHP Serializer - Serialize JSON in PHP format") — the fourth pair to declare
      `OperationGroup.ENCODERS_DECODERS`.** Backs `POST /api/v1/dev-utils/php-serialize/
      {serialize,unserialize}`. Genuinely different from this module's existing
      `PhpToJsonOperation`/`JsonToPhpOperation` (which convert between JSON and PHP array-literal
      *source code*, `['key' => 'value']`) — this pair converts between JSON and PHP's own
      `serialize()`/`unserialize()` *wire format* (`a:N:{...}`), via two new support classes
      (`PhpSerializeWriter`/`PhpSerializeParser`) mirroring the existing `PhpArrayWriter`/
      `PhpArrayParser` split. String lengths (`s:L:"...";`) are counted in UTF-8 bytes, not Java
      chars — verified against a real standalone Java harness for a genuinely multi-byte example
      (`"Hi👋"`, a surrogate pair in Java) before writing the matching tests, on both the writer and
      parser sides. `PhpSerializeOperation` reuses `INVALID_JSON` (same choice `JsonToPhpOperation`
      already makes); new `DevUtilsErrorCode.INVALID_PHP_SERIALIZED` (`DEVUTILS_008`) backs
      `PhpUnserializeOperation`'s own real failure path. `gui`'s `config/operations.tsx` gained the
      `php-serializer` entry (reusing the existing `PhpOutlined` icon, no new GUI capability
      needed); `api/devUtilsApi.ts` gained `serializePhp`/`unserializePhp` — both use
      `inputFormat: 'text'` (not `'json'`) since the shared input box also serves Unserialize's own
      non-JSON input. 24 new backend tests (198→222 — `PhpSerializeWriterTest`/
      `PhpSerializeParserTest`/`PhpSerializeOperationTest`/`PhpUnserializeOperationTest` plus 3 new
      `DevUtilsServiceApplicationTests` cases), plus a clean `tsc --noEmit`/successful `vite build`
      on the GUI side — no Docker in this sandbox, so the actual two-button GUI flow is unverified
      in a real browser.
    - **Follow-up: `HashGeneratorOperation`/`hash-generator` moved from the `Inspectors` group to
      `Encoders/Decoders`, per direct request** — a hash digest reads as a one-way encoding of a
      value more than an "inspection" of one, and this keeps every operation added since the
      original 16 Formatters ones under one sidebar section. Just `group()`'s return value
      (backend) and `config/operations.tsx`'s `group`/`category` fields (GUI) — no behavior
      change, and no effect on which panel renders it (`HashGeneratorPanel` is still keyed by
      operation `key`, not `group`). `OperationGroup.INSPECTORS` is back to fully
      declared-ahead-of-use as a result (only its own "a JWT decoder" example remains unbuilt).
    - **Follow-up: `php-serializer`'s `label`/`description` renamed to name both directions
      explicitly, per direct request** — `'PHP Serializer'` → `'PHP Serializer/Unserializer'`
      (matching `url-string`'s own `'URL Encode/Decode'` naming), description updated to mention
      both Serialize and Unserialize. Display-only, no behavior change.
    - **Follow-up: 2 new operations, `AsciiToHexOperation`/`HexToAsciiOperation`, per request
      ("Hex to ASCII, ASCII to Hex") — the fifth pair to declare
      `OperationGroup.ENCODERS_DECODERS`.** Backs `POST /api/v1/dev-utils/hex/{encode,decode}`.
      Encode converts the input's own UTF-8 bytes into lowercase, space-separated hex pairs via
      `HexFormat.ofDelimiter(" ")` — confirmed byte-for-byte against the exact reported example via
      a real standalone Java harness first; never throws. Decode strips all whitespace then parses
      the remainder via `HexFormat.of().parseHex(...)`, tolerant of space-separated, unseparated, or
      arbitrarily-whitespace-separated hex; new `DevUtilsErrorCode.INVALID_HEX` (`DEVUTILS_009`)
      backs its real failure path (an odd digit count or a non-hex character). `gui`'s
      `config/operations.tsx` gained one `hex-ascii` entry (not two separate tools, matching every
      prior Encode/Decode pair in this group) with `label`/`description`/action labels all naming
      both directions explicitly (`'ASCII/Hex Converter'`, `'ASCII to Hex'`/`'Hex to ASCII'`); new
      `HexagonOutlined` icon; `api/devUtilsApi.ts` gained `encodeHex`/`decodeHex`. 13 new backend
      tests (222→235 — `AsciiToHexOperationTest`/`HexToAsciiOperationTest` plus 3 new
      `DevUtilsServiceApplicationTests` cases), plus a clean `tsc --noEmit`/successful `vite build`
      on the GUI side — no Docker in this sandbox, so the actual two-button GUI flow is unverified
      in a real browser.
    - **Follow-up: "Base64 Image" (convert images to Data URLs and back, with a live preview), per
      direct request — `gui`-only, no backend endpoint or operation class at all.** Converting a
      file to a Data URL is a pure browser `FileReader#readAsDataURL` operation, so round-tripping
      raw image bytes through `dev-utils-service` just to base64-encode them would only add
      latency/payload size for zero benefit — the first operation in this feature with no 1:1
      backend counterpart. New `gui` `components/Base64ImagePanel.tsx` (the second custom-layout
      operation after Hash Generator): two Input boxes (drag-and-drop/file-picker upload, and a
      plain "Image Data URL" text box either can be pasted into), both writing to the same lifted
      `input` string, and an Output "Preview" box that's just `<img src={input}>`.
      `config/operations.tsx`'s `OperationConfig.onSubmit` is now optional to accommodate this
      operation's genuine lack of a backend call — the two branches that do require it
      (`DevUtilToolPanel`/`HashGeneratorPanel`) assert it non-null, safely, since every operation
      routed to either always supplies one. Verified via a clean `tsc --noEmit`/successful
      `vite build` only — no Docker in this sandbox, so the actual upload/drag-drop/paste/preview
      flow is unverified in a real browser.
    - **Follow-up, 5 fixes reported together after real use**: (1) Copy/Download added to the
      Preview box itself (Download decodes the Data URL back into a real image file via new
      `dataUrlToBlob`/`parseDataUrl` helpers and the same `<a download>`/Blob-URL pattern
      `DevUtilToolPanel.tsx` already uses for text). (2) Pasting an actual image (e.g. a
      screenshot) into the Data URL box now works too, via a new `onPaste` handler routing a
      clipboard image through the same `FileReader` path the Upload box uses — pasting a Data URL
      *string* already worked and needed no change. (3) File type restricted to a fixed allow-list
      (PNG/JPG/GIF/WebP/SVG, replacing the previous, more permissive `image/*` check) via a new
      `ALLOWED_IMAGE_TYPES` map, also driving the file input's own `accept` attribute and the
      Download button's extension. (4) `fontWeight={600}` added to "Drop or select an image".
      (5) The visible "box inside a box" borders removed from both Input boxes — the Upload box's
      drop-zone border only via hover/drag `bgcolor` now, and the Data URL `TextField`'s own
      default outline hidden, the same fix already established for `HashGeneratorPanel.tsx`'s
      Input box. Verified via a clean `tsc --noEmit`/successful `vite build` only — no Docker in
      this sandbox, so all five fixes are unverified in a real browser.
    - **Follow-up, per request: a checkerboard transparency pattern replaces the Preview box's
      flat white background** (new `CHECKERBOARD_BACKGROUND`, a `repeating-conic-gradient`), the
      standard way image tools show a surface that might have an alpha channel; **and the Preview
      card's own height now matches the shared `availableHeight`** the sidebar/`DevUtilToolPanel`
      Input card already use, via a new `availableHeight` prop threaded from `DevUtilsPage.tsx`
      (fixed `height`, not `minHeight` — Preview's content is one bounded image, not open-ended
      text). Verified via a clean `tsc --noEmit`/successful `vite build` only — no Docker in this
      sandbox, so both changes are unverified in a real browser.
    - **Follow-up, per request: the checkerboard now shows only while a real image is loaded**
      (plain white for the empty/failed states instead), **and a "Paste" button was added to the
      Image Data URL box** — tries `navigator.clipboard.read()` first (an actual clipboard image
      takes the same upload path a real file does) and falls back to `readText()` for a plain
      Data URL string. Verified via a clean `tsc --noEmit`/successful `vite build` only — no
      Docker in this sandbox, so neither change is verified in a real browser.
  - See `dev-utils-service/CLAUDE.md` for the full module writeup, and root `CLAUDE.md`'s Module
    Structure table, Long-term direction, Security, Database Conventions, and Architecture →
    Routing sections for the reactor-wide documentation updates this addition required.

### Fixed

- **`dev-utils-service` — a code-quality analysis pass (same shape as the earlier `gui`
  dev-utils analysis) surfaced 2 real bugs and a handful of duplication/doc-drift/Javadoc-coverage
  issues; all implemented in one pass.**
  - **`service.impl.support.CurlyBraceFormatter` mishandled an unquoted `url(http://...)`
    argument** (e.g. `background: url(http://example.com/x.png);`, a very common CSS pattern) — its
    line-comment scanner had no awareness of "inside an unquoted `url()` argument" and misread the
    `//` after the scheme colon as a comment start. In `beautify` this broke brace-depth tracking
    for everything after it; in `minify` — worse — a single-line declaration has no `\n` to stop the
    scan, so everything from the `//` to the end of the input was silently **discarded**. Fixed by
    treating an unquoted `url(...)` argument as one atomic span (new
    `isUrlFunctionStart`/`scanUrlFunctionArg` helpers), the same way a quoted string literal already
    was — a quoted `url("...")` is untouched, since it was already protected by the existing
    string-literal handling. 3 new regression tests in `CurlyBraceFormatterTest`.
  - **`service.impl.support.PhpArrayParser#parseNumber()` could throw an uncaught
    `NumberFormatException`** for an integer literal wider than a `long` (22+ digits) or an
    incomplete exponent (e.g. `1e`) — neither is a `PhpParseException`, so both skipped
    `PhpToJsonOperation`'s own catch clause entirely and surfaced as a generic `500` instead of the
    clean `400`/`INVALID_PHP` this parser exists to produce for malformed input (the same failure
    shape this module has now hit and fixed three times — see the `BusinessException`
    varargs-template and CSV `RuntimeJsonMappingException` fixes already documented in
    `dev-utils-service/CLAUDE.md`). Fixed by wrapping the `Long.parseLong`/`Double.parseDouble`
    calls in a try/catch, rethrown via the same `errorAt(...)` helper every other parse failure in
    this class already uses. 2 new regression tests in `PhpArrayParserTest`.
  - **New `service.impl.support.JsonNodeIo`** — two static helpers (`readTree`/`write`) factoring
    out the "parse JSON, throw a clean `BusinessException` on failure" and "serialize pretty vs.
    minified" blocks that had been copy-pasted near-verbatim across `JsonFormatOperation`,
    `YamlToJsonOperation`, `JsonToYamlOperation`, `JsonToCsvOperation`, `JsonToPhpOperation`,
    `CsvToJsonOperation`, and `PhpToJsonOperation` — roughly 8 duplicated blocks across 7 classes,
    each caller keeping its own error code (and, for `readTree`, whatever it does with the parsed
    tree afterward), so this doesn't force operations with genuinely different shapes through one
    common method the way `service.DevUtilOperation`'s own Javadoc warns against for the operations
    themselves.
  - **Doc drift fixed in 4 files** that still described the module at its original 3-4-operation
    size, not its current 16: `dto.MinifiableTextRequest`/`dto.TextRequest` (both claimed to be
    shared by operations that had since grown well past the list named), `DevUtilsServiceApplication`'s
    own class Javadoc (still listed only JSON/YAML/HTML), and `dto.DevUtilResponse` (described
    `StringCaseResponse`'s multi-value-response case as hypothetical future work when it already
    exists today).
  - **Every one of the 16 operations' `execute(...)` methods (plus
    `exception.ParsingExceptionMessages#friendlyMessage`) gained method-level Javadoc** — per this
    project's own root `CLAUDE.md` rule ("Javadoc for every … public method"), which this module's
    otherwise-thorough class-level Javadoc had drifted away from at the method level across all 16
    operations.
  - Test suite grew from 133 to 138 (5 new regression tests, 0 new failures) — verified via a real
    `mvn -pl dev-utils-service -am test` run (JDK 21), same as every other change to this module.
- **`dev-utils-service` — follow-up bug, reported directly against a real payload: the JSON Format
  tool's pretty-printed output diverged from conventional JSON formatting (`"key" : value`,
  single-line space-padded arrays `[ "a", "b" ]`, `[ ]` for an empty array instead of `[]`).**
  New `service/impl/support/ConventionalJsonPrettyPrinter` replaces
  `ObjectMapper#writerWithDefaultPrettyPrinter()` in `JsonNodeIo.write` — Jackson's own default
  pretty printer genuinely diverges from what every mainstream JSON formatter produces on 3 counts
  (space on both sides of `:`, arrays rendered single-line instead of one-element-per-line, an
  empty container padded to `[ ]`/`{ }` instead of collapsed to `[]`/`{}`), all fixed by this new
  class. **A 4th bug was caught only by verifying the fix byte-for-byte with a standalone Java
  harness rather than trusting a test-failure diff**: the first cut left Jackson's own
  `DefaultIndenter.SYSTEM_LINEFEED_INSTANCE` in place, whose line ending follows
  `System.lineSeparator()` — CRLF on the Windows dev machine, LF wherever this service actually
  deploys (Linux Docker) — making output silently platform-dependent, and coincidentally producing
  an AssertJ diff that *looked* like a doubled-indentation bug (a stray `\r` before an inserted
  `\n` marker renders as a carriage return in a terminal). Fixed by constructing both indenters
  explicitly with a literal `"\n"`. Since every operation that produces pretty JSON goes through
  `JsonNodeIo.write` (JSON Format, YAML→JSON, CSV→JSON, PHP→JSON), the fix applies to all four at
  once. 5 new tests in a dedicated `ConventionalJsonPrettyPrinterTest` (the one support class in
  this module with its own test file rather than only being exercised indirectly via an
  operation's own tests) plus a tightened `JsonFormatOperationTest` assertion. Test suite grew from
  138 to 144.
- **`dev-utils-service` — second follow-up, same bug shape, reported directly against a real
  payload: the JSON→YAML tool's output diverged from conventional YAML formatting too** (a leading
  `---` document marker; every string quoted regardless of need, e.g. `"Vui Coding"`; a block
  sequence's `-` indicator rendered at the same column as its parent key instead of indented under
  it). Root cause was `config/YamlMapperConfig`'s `YAMLMapper.builder().build()` call — Jackson's
  own stock `YAMLGenerator.Feature` defaults, same "genuinely diverges from every mainstream
  formatter" bug class as the JSON pretty-printer fix above. Fixed with 3 builder overrides
  (`WRITE_DOC_START_MARKER` disabled, `MINIMIZE_QUOTES` enabled, `INDENT_ARRAYS_WITH_INDICATOR`
  enabled — **not** the plainer-sounding `INDENT_ARRAYS`, which was tried first and rejected once
  measured, since it only indents the `-` by 1 space instead of the conventional 2), all verified
  against the exact reported input via a standalone Java harness before landing. Confirmed
  `USE_PLATFORM_LINE_BREAKS` was already `false` by default, so (unlike the JSON pretty-printer's
  own fix) this operation's YAML output was never platform-dependent to begin with.
  `JsonToYamlOperationTest`'s `setUp()` now builds its `yamlMapper` via
  `new YamlMapperConfig().yamlMapper()` instead of a second, bare `YAMLMapper.builder().build()`
  call, so the test can never again silently drift from the real bean's own configuration, plus a
  new test locking in the exact expected byte sequence. `YamlToJsonOperation` (the read direction)
  is unaffected. Test suite grew from 144 to 145.
- **`dev-utils-service` — third follow-up, same bug shape, reported directly against a real CSS
  example: `CurlyBraceFormatter`'s "beautify never spaces a `:`" behavior was previously a
  deliberate, documented limitation (avoiding corruption of `:hover`/`&:hover`), but turned out to
  be genuinely fixable once the reported example made clear the two contexts are locally
  distinguishable.** Two bounded checks now tell a declaration's colon (`display:grid` → gets a
  space) from a selector's (`.card:hover`/`&:hover` → stays untouched): a running `parenDepth`
  counter (a colon already inside parens — a media feature, `@media (min-width: 768px)` — is
  always declaration-style) and, otherwise, a forward lookahead (`selectorFollowsBeforeStatementEnd`)
  for whichever of `{`/`;`/`}` comes first from the colon. Existing spacing around the colon is
  first normalized to exactly one space rather than doubling up when a space is added. Separately,
  a blank line is now inserted after a `}` that closes a rule back down to brace-nesting depth `0`
  (a genuinely top-level rule) — two adjacent top-level rules used to render with no visual break.
  Both fixes are `beautify`-only, shared uniformly by `CssOperation`/`LessOperation`/
  `ScssOperation`/`JsOperation` (this formatter's own "one shared reformatter" design, not
  special-cased per operation); `minify` needed no change, since `:` was already stripped of all
  surrounding whitespace unconditionally there. 5 new tests in `CurlyBraceFormatterTest`, plus 2
  existing tests and one end-to-end `DevUtilsServiceApplicationTests` assertion updated from an
  unspaced-colon expectation to a spaced one. Test suite grew from 145 to 150.
- **`dev-utils-service` — fourth follow-up, same bug shape, reported directly against a real LESS
  example: the blank-line-between-top-level-rules fix above was too narrow (missed the identical
  gap one level deeper), plus a separate comma-spacing gap in the same report.**
  `.button { background: @brand; &:hover {...} }` — a declaration followed by a nested rule
  *inside* a block — used to render with no blank line between them. Generalized (not
  re-implemented): `CurlyBraceFormatter` now tracks, per brace-nesting depth, whether the block
  currently at that depth already has prior content (`hasContentAtDepth`), and a blank line goes in
  before any line that opens a nested rule whenever its own block already had one — the old,
  top-level-only "blank line after a `}` reaching depth 0" logic was removed outright, superseded
  by this general rule. **A real bug caught in the same pass, by an existing test failing**: the
  first cut used `atLineStart` alone to detect "a new statement begins here" — wrong, since an
  already-multiline selector list (`h1,\nh2 {...}`) also sets that flag true on its second line,
  which isn't a new statement, just a preserved line-wrap of the first; a second, narrower flag
  (`atStatementStart`, set only by `;`/`{`/`}`) fixed it. **Separately, `beautify` now normalizes
  same-line spacing after a `,`** (`darken(@brand,10%)` → `darken(@brand, 10%)`) — unlike the colon
  fix, this needed no context check, since a space after `,` is unconditionally correct in a
  function argument list, a selector list, or a JS array/object literal alike; skipped only before
  a newline (preserves an already-multiline list) or a closing `)`/`]`/`}` (no space padding a
  trailing comma from its closer). 3 new tests in `CurlyBraceFormatterTest`, plus a fortified
  `preservesAlreadyMultilineSelectorLists` and an updated `indentsNestedBlocksByDefault`. Test
  suite grew from 150 to 153.
- **`dev-utils-service` — fifth follow-up, two real issues reported together against JSON↔CSV.**
  `JsonToCsvOperation` silently quoted a value for containing nothing more than a plain space (e.g.
  `JSON Formatter` → `"JSON Formatter"`) — found by decompiling Jackson's own `CsvEncoder`:
  `CsvMapper`'s default ("loose") quoting check quotes any value containing *any* character below
  ASCII 45, not just what RFC 4180 actually requires. Fixed by enabling
  `CsvGenerator.Feature.STRICT_CHECK_FOR_QUOTING`, verified via a standalone harness confirming both
  a plain-space value now stays unquoted and a value genuinely needing quoting still gets it.
  Separately, `CsvToJsonOperation` gained one deliberately narrow exception to its own "every value
  comes back as a string" design — a cell that's exactly `true`/`false` (case-insensitive) now
  becomes a real JSON boolean, every other value (numbers included) still stays a string. **This
  was a real design question, not an obvious bug fix** — confirmed the scope with the user before
  implementing (booleans only vs. booleans + numbers vs. leave as-is), since the original "never
  guess a type" rule has a concrete, still-valid reason (a ZIP code like `"007"` would lose its
  leading zero as a number) that booleans alone don't share. 4 new tests total across both
  operations. Test suite grew from 153 to 157.
- **`dev-utils-service` — sixth follow-up, reported directly against a real SQL example — unlike
  every prior fix in this run, this one reversed two previously *deliberate* `SqlFormatter` design
  choices (verbatim keyword casing, never splitting a comma-separated list) rather than fixing an
  oversight, so the scope was confirmed with the user before implementing (full expected style,
  uppercase-only, list-splitting-only, or leave as-is — full expected style was chosen).**
  `SqlFormatter` now classifies every recognized keyword into one of three roles: `TOP_LEVEL_CLAUSE`
  (`SELECT`/`FROM`/`WHERE`/etc. — own fresh line, body starts on its own further-indented line);
  `BODY_BREAK` (every `JOIN` variant, `AND`, `OR` — fresh line *within* the current clause's own
  body indent, not a new top-level line, which is what keeps `LEFT JOIN posts p ON p.user_id =
  u.id` together on one line; `AND`/`OR` also lost their own extra indent level in the same pass,
  now sharing the plain body-level indent `JOIN` gets); `INLINE` (`ON`, `AS`, `ASC`, `DESC`,
  `TRUE`, `FALSE`, `NULL`, `NOT`, `IN`, `LIKE`, `IS`, `BETWEEN`, `EXISTS`, `DISTINCT` — uppercased,
  never breaks a line). A comma now splits a list onto one item per line, scoped to the *current*
  clause's own base paren depth, so a comma inside a function call's own argument list
  (`count(id, other)`) or a subquery stays correctly inline. `matchKeywordPhrase` was tightened to
  always prefer the longest matching phrase regardless of any one list's own declaration order. A
  function name that happens to also be a common SQL built-in (`COUNT`/`SUM`/`AVG`/...) is
  deliberately excluded from all three keyword lists — it's an identifier, not a keyword. Known,
  accepted imprecision (not chased further): a scalar subquery inside a `SELECT` list item leaves
  its own opening `(` alone on its own line, since the nested `SELECT` immediately after it forces
  its own fresh line too — an unusual-looking layout for that one nested shape, but not a
  correctness issue. `SqlFormatterTest` grew from 10 to 14 tests (most of the original 10 also
  rewritten to match the new behavior); one stale lowercase-keyword assertion each fixed in
  `SqlFormatOperationTest` and `DevUtilsServiceApplicationTests`. Test suite grew from 157 to 161.
- **`dev-utils-service` — seventh follow-up, a small one, reported directly against a real
  payload: `PhpArrayWriter#write`'s own non-minify output was missing a blank line between
  `<?php` and `return`** — the standard convention this snippet's own shape is meant to evoke.
  Fixed by writing `"<?php\n\nreturn "` instead of `"<?php\nreturn "`; `minify`'s own single-line
  output is untouched. `PhpArrayParser` needed no change, since it already tolerates arbitrary
  whitespace between tokens. 4 existing exact-match tests updated (3 in `PhpArrayWriterTest`, 1 in
  `JsonToPhpOperationTest` — the exact reported example); test suite count unchanged at 161.

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
