# CLAUDE.md — dev-utils-service

Module-local guidance for `dev-utils-service`. Read alongside the root `CLAUDE.md`.

## What lives here

A stateless developer-utility API: JSON format/validate, YAML↔JSON conversion, HTML/CSS/LESS/SCSS/
JS/ERB beautify+minify, XML validate/beautify+minify, JSON↔CSV conversion, SQL format+minify,
PHP↔JSON conversion, String Case Converter, Base64 encode/decode, URL encode/decode, HTML entity
encode/decode. Package root: `com.ttg.devknowledgeplatform.devutils.*`.

**A standalone Spring Boot application from day one — not an extraction from anything.** Unlike
`ecommerce-service`/`identity-service`/`task-service`/`social-service`/`content-service`/
`ai-service` (all six pulled out of an embedded monolith module — see the
`project-microservices-extraction-plan` memory), this module never lived inside `gateway`; it was
built standalone from the start, following the "new big feature area gets its own module" rule in
root `CLAUDE.md`. It is **not part of the microservices-extraction-plan project** (that project is
closed — see root `CLAUDE.md`'s Long-term direction section) — don't read this module's existence
as reopening it.

**The one deployable in this reactor with genuinely nothing to persist and no authenticated
caller.** Every operation is a pure text-in/text-out transform:

- **No Postgres schema, no JPA entity, no Liquibase changelog at all** — the only deployable in
  the reactor with zero database story of any kind. Not in `services-liquibase`'s `for svc in ...`
  loop, no changelog directory to mount. **`DevUtilsServiceApplication` excludes
  `DataSourceAutoConfiguration`/`HibernateJpaAutoConfiguration` — not optional, found by an actual
  boot attempt.** `common` declares `spring-boot-starter-data-jpa` non-optional (for
  `AbstractEntity`'s `@MappedSuperclass`), which every consumer inherits transitively regardless of
  whether it maps any entities. Every other service in this reactor never notices, since each one
  already configures a real `spring.datasource.url` + `org.postgresql:postgresql`; left
  un-excluded here, Spring Boot tried to build a `HikariDataSource` anyway and the app failed to
  start at all (`DataSourceBeanCreationException: Failed to determine a suitable driver class`) —
  caught by a real `@SpringBootTest` context-load failure, not anticipated up front.
- **Every endpoint is public — no JWT verification at all.** `security/SecurityConfig` declares an
  explicit `.anyRequest().permitAll()` filter chain rather than the `.anyRequest().authenticated()`
  every other service in this reactor uses. See that class's own Javadoc for the two-layer reason
  this needed a real filter chain rather than just omitting Spring Security: `gateway`'s own
  `SecurityConfig` gates `/api/v1/**` behind `.anyRequest().authenticated()` *before* it ever
  proxies anywhere, so `/api/v1/dev-utils/**` needed its own `permitAll()` carve-out there too (see
  `gateway/CLAUDE.md`) — and even with that carve-out in place, this module's own classpath still
  needs `spring-boot-starter-security` regardless of whether it authenticates anyone: `common`'s
  shared `GlobalExceptionHandler` declares `@ExceptionHandler` methods over
  `org.springframework.security.access.AccessDeniedException`/
  `org.springframework.security.core.AuthenticationException`, both of which Spring resolves via
  reflection when that bean registers at context startup — without the dependency present, that
  bean fails to load with a `NoClassDefFoundError` before this app ever serves a request. Given the
  dependency is unavoidable, `SecurityConfig` exists so Spring Boot's own autoconfigured default
  (HTTP Basic + a generated per-boot password, `.anyRequest().authenticated()`) never actually
  applies to anything here. No `spring-boot-starter-oauth2-resource-server`/`-oauth2-client` at
  all — nothing here ever verifies a JWT, so neither dependency is needed.
- **No `@CurrentUserId` consumer, no `CustomUserOAuth2` principal, no Keycloak-related import on
  `DevUtilsServiceApplication`** — there is no authenticated caller to resolve.
- Own port **`8087`** (the next free port after `ai-service`'s `8086`).

- `DevUtilsServiceApplication` — `@SpringBootApplication` +
  `@Import({JacksonConfig.class, TraceContextFilter.class, GlobalExceptionHandler.class})`. Names
  the exact `infra`/`common` beans this module uses, same convention every other standalone
  service in this reactor follows (see root `CLAUDE.md`'s "Post-extraction hardening" section) —
  `JacksonConfig` (the JSON format/YAML conversion operations depend on this exact `ObjectMapper`
  bean) and `TraceContextFilter` (reactor-wide tracing/access logging). No Keycloak-related
  import, no `CurrentUserIdArgumentResolver`.
- `security/SecurityConfig` — see above.
- `exception/DevUtilsErrorCode` — `INVALID_JSON`/`INVALID_YAML`/`INVALID_XML`/`INVALID_CSV`/
  `INVALID_PHP`/`INVALID_BASE64`. No `INVALID_HTML`/`INVALID_CSS`/`INVALID_LESS`/`INVALID_SCSS`/
  `INVALID_JS`/`INVALID_SQL` — jsoup's parser (HTML, and `ErbOperation`'s own jsoup-based approach) is
  deliberately lenient and never throws on malformed markup, `CssOperation`/`LessOperation`/
  `ScssOperation`/`JsOperation` all delegate to the equally lenient `service/impl/support/
  CurlyBraceFormatter` (see below), and `SqlFormatOperation` delegates to the similarly lenient
  `service/impl/support/SqlFormatter` (see below) — none of these six have an invalid-input
  failure path to name. `StringCaseOperation`/`Base64EncodeOperation` are the same story for a
  different reason each: the former is a pure text transform (split into words, re-case/re-join)
  with no notion of "invalid" input at all; the latter is that every string has *some* valid
  Base64 encoding, so there's nothing to reject either. `INVALID_XML`/`INVALID_CSV`/`INVALID_PHP`/
  `INVALID_BASE64` are the exceptions among the newer operations:
  `XmlOperation`/`CsvToJsonOperation`/`PhpToJsonOperation`/`Base64DecodeOperation` are backed by
  real parsers/validators (JAXP, Jackson's `CsvMapper`, this module's own
  `service/impl/support/PhpArrayParser`, and the JDK's own `java.util.Base64.Decoder`
  respectively), the same "real parse, real invalid-input error" shape `INVALID_JSON`/
  `INVALID_YAML` already establish. `JsonToCsvOperation`/`JsonToPhpOperation` both reuse
  `INVALID_JSON` rather than getting their own code — their input is JSON either way, so a
  failure there (a genuine syntax error, or — for `JsonToCsvOperation` only — valid JSON in a
  shape that can't become rows) is still honestly "Invalid JSON."
  - **Fixed, per direct follow-up request, a bug originally found while investigating a `gui`
    error-message complaint (see that complaint's own history below for the client-side half of
    this story).** `INVALID_JSON`/`INVALID_YAML`'s own `"Invalid JSON: {0}"`/`"Invalid YAML: {0}"`
    templates were defined but never actually applied — `JsonFormatOperation`/`JsonToYamlOperation`/
    `YamlToJsonOperation` all caught their `JsonProcessingException` and called
    `new BusinessException(errorCode, e.getMessage())`, a plain `String` argument that
    overload-resolves to `BusinessException(ErrorCode, String message)` (the raw-message
    constructor), never the varargs `(ErrorCode, Object... templateArgs)` overload that runs the
    message through `ErrorCode.formatMessage()`. So the client-facing `errorMessage` used to be
    Jackson's own raw exception text verbatim — no "Invalid JSON:"/"Invalid YAML:" prefix despite
    the enum implying one, and, worse, it leaked Jackson's own parser-internals diagnostics
    (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION`, `[Source: REDACTED (...)]`) straight to any
    API caller. **Two-part fix, both confirmed with a real standalone Java harness against the
    actual resolved Jackson 2.19.2 (not just read and assumed):**
    1. **New `exception/ParsingExceptionMessages` (`friendlyMessage(JsonProcessingException e)`)**
       builds a clean detail string structurally, not by string-parsing `e.getMessage()`: it starts
       from `e.getOriginalMessage()` (Jackson's own pre-location-suffix message, so
       `getMessage()`'s own separately-appended `[Source: ...]` clause is gone for free), strips
       two further noise shapes via regex (Jackson's own inline `"(start marker at [Source:
       ...])"` clause some structural errors bake directly into their original message — confirmed
       real via the harness, e.g. an unclosed `{` — and SnakeYAML's own `"in '<name>', line N,
       column M:"` mark blocks, each followed by a 2-line source-snippet + `^`-pointer, which a
       single YAML error can carry *twice* — a "context" mark and a "problem" mark, interleaved
       with the two sentences that actually explain the failure, confirmed via the harness against
       a real malformed-flow-sequence YAML input), then re-appends the real location from
       `e.getLocation()` (a structured `JsonLocation` — `getLineNr()`/`getColumnNr()`, never
       string-parsed). Harness output for 4 real inputs: an unclosed JSON object → `"Unexpected
       end-of-input: expected close marker for Object (line 1, column 14)"`; a missing-comma JSON
       object → `"Unexpected character ('"' (code 34)): was expecting comma to separate Object
       entries (line 1, column 15)"`; a malformed YAML flow sequence (`tools: [JSON,,Base64]`,
       both SnakeYAML marks) → `"while parsing a flow node expected the node content, but found
       ',' (line 1, column 13)"`; a bad YAML indent → `"mapping values are not allowed here (line
       2, column 6)"` — all four clean, single-line, zero Jackson/SnakeYAML internals.
    2. **Each operation's catch block now passes that clean message through a `(Object)` cast**:
       `new BusinessException(errorCode, (Object) ParsingExceptionMessages.friendlyMessage(e))`.
       The cast is load-bearing, not decorative — it's what actually fixes the "template never
       applied" half of the bug: a plain `String` argument always resolves to
       `BusinessException(ErrorCode, String message)` in Java's overload resolution (phase 1, no
       boxing/varargs needed, since the argument already *is* a `String`); casting to `Object`
       makes that overload inapplicable (an `Object` doesn't implicitly narrow to `String`),
       forcing resolution onto the varargs `(ErrorCode, Object... templateArgs)` overload instead
       — the one that actually calls `ErrorCode.formatMessage()`. Confirmed end-to-end via the same
       harness (not just each half in isolation): a real `readTree` failure fed through both fixes
       together produced `errorMessage = "Invalid JSON: Unexpected end-of-input within/between
       Object entries (line 1, column 79)"` — the template prefix finally applies, and the message
       is clean.
    - **`gui`'s own `dev-utils/utils/errorFormatting.ts` keeps its client-side logic regardless of
      this fix — it was never purely a workaround for this bug, and this fix doesn't make it dead
      code.** For a JSON-input operation, the browser's own `JSON.parse` still produces a *more*
      precise, more familiar message (native V8 phrasing) than this service's Jackson-based one
      ever could, so that client-side path stays as the better option, not a stopgap. For
      `yaml-to-json` (the one operation whose message the GUI still shows verbatim from this
      service), `errorFormatting.ts`'s own `simplifyBackendMessage` was updated in the same pass to
      stay idempotent against this now-already-clean message (it used to unconditionally re-append
      its own `"(line N, column M)"` suffix, which would have doubled up against this service's own
      new one) — see that file's own doc comment.
- `service/DevUtilOperation` — for most of this module's life, a bare **marker interface** (no
  method), purely for IDE "Find Implementations" grouping — the same role
  `infra.event.ApplicationEventHandler`/`infra.service.seed.Seeder` already play in this reactor.
  Deliberately **not** a textbook GoF Strategy with a shared `execute(...)` signature — an earlier
  revision forced every operation through `execute(String input, boolean minify): String`, which
  broke down once a genuinely different-shaped operation (a future Unix Time Converter needing
  timestamp+timezone+format, a Number Base Converter needing value+two integer bases) was
  considered — neither fits "one string in, one bool flag, one string out," and packing them into
  that shape would mean hand-parsing a packed string apart instead of real typed parameters. A
  shared method signature only pays for itself when something dispatches through it
  polymorphically; nothing here does — the controller injects and calls each operation by its own
  concrete type. See its own Javadoc for the full reasoning. Each operation is free to declare
  whatever parameter/return shape actually fits it.
  - **Gained one real abstract method, `group(): OperationGroup`, per direct request — "Group all
    the existing Operation in a group calls 'FORMATTERS' since we will implement new operations
    belongs to another group (ENCODERS/DECODERS, INSPECTORS, WEB, GENERATORS) next."** This is the
    one deliberate exception to the interface's own "no shared method" philosophy above — `group()`
    doesn't reintroduce the "forced shared `execute()` shape" problem the marker-interface design
    exists to avoid, since it's closer to `Object#toString()` than to a Strategy's own
    `execute(...)`: every operation, regardless of its own wildly different `execute(...)` shape,
    genuinely has exactly one answer to "which page-level section does this belong to," the same
    way every object has some string representation. New `service/OperationGroup` enum —
    `FORMATTERS`/`ENCODERS_DECODERS`/`INSPECTORS`/`WEB`/`GENERATORS`, each with a Title-Case
    `getLabel()` (not ALL-CAPS — the GUI sidebar applies its own CSS `text-transform`, matching the
    existing "Tools" caption convention already established there). All 16 existing operations
    declare `FORMATTERS`; the other 4 groups are declared **ahead of use**, with a concrete example
    operation named in the enum's own Javadoc for each (a Base64/URL encoder for
    `ENCODERS_DECODERS`, a JWT decoder/hash calculator for `INSPECTORS`, an HTTP header parser for
    `WEB`, a UUID/Lorem-Ipsum generator for `GENERATORS`) — so the GUI's own group-by-label sidebar
    rendering already has a complete, stable section order to render from day one, rather than
    needing a second change once the first non-`FORMATTERS` operation actually lands. **`group()`
    is abstract, not `default`** — a `default OperationGroup group() { return
    OperationGroup.FORMATTERS; }` would let a future non-formatter operation silently inherit the
    wrong group if its author forgot to override it; abstract forces a compile error instead,
    catching the omission immediately rather than as a quiet mis-grouped sidebar entry. All 16
    operation classes (`CssOperation`, `CsvToJsonOperation`, `ErbOperation`,
    `HtmlBeautifyOperation`, `JsonFormatOperation`, `JsonToCsvOperation`, `JsonToPhpOperation`,
    `JsonToYamlOperation`, `JsOperation`, `LessOperation`, `PhpToJsonOperation`, `ScssOperation`,
    `SqlFormatOperation`, `StringCaseOperation`, `XmlOperation`, `YamlToJsonOperation`) implement it
    identically (`return OperationGroup.FORMATTERS;`). No new `DevUtilsErrorCode`, no request/
    response DTO change — this is a pure classification addition, orthogonal to every operation's
    own input/output shape. Verified via a full `mvn -pl dev-utils-service -am test` run (JDK 21)
    after the change — **161/161 tests still passing, 0 failures** (a compile-time-only addition, so
    no test assertions needed updating). See `gui/CLAUDE.md`'s own dev-utils section for the
    matching sidebar-headline GUI half of this feature.
- `config/YamlMapperConfig` — a `YAMLMapper` `@Bean`, the YAML-side counterpart to `infra`'s
  shared `ObjectMapper` (`JacksonConfig`). Lives here, not `infra` — this module is the only
  consumer today; promote it there only once a second module genuinely needs the same bean.
  Mirrors `JacksonConfig`'s own customization (`JavaTimeModule`, tolerant deserialization,
  ISO-8601 dates) for consistency, even though neither operation below can currently observe a
  difference (both work over a generic `JsonNode` tree, never a typed POJO).
- `service/impl/{JsonFormatOperation,YamlToJsonOperation,JsonToYamlOperation,HtmlBeautifyOperation}`
  — one `@Component` per operation. `JsonFormatOperation` validates and pretty-prints (or, with
  `minify`, compact-serializes) in one pass (doubles as "JSON validate"). `YamlToJsonOperation`
  applies the same pretty/minify choice to its JSON output; both it and `JsonToYamlOperation`
  inject the shared `ObjectMapper`/`YAMLMapper` beans — no operation constructs its own mapper.
  **`JsonToYamlOperation` accepts but ignores `minify`** — `jackson-dataformat-yaml` has no
  supported single-line/flow-style toggle, so output is always the same block-style YAML
  regardless of the flag. `HtmlBeautifyOperation` parses input as a body fragment
  (`Jsoup.parseBodyFragment`), not a full document — a snippet in yields a snippet out; a full
  `<html>` document's `<head>` is dropped, the same trade-off most standalone HTML-beautifier
  tools make. `minify` maps to jsoup's own `prettyPrint(false)` mode — not a true single-line
  guarantee (whitespace already present inside a source text node is preserved as-is).
- **6 new operations (`CssOperation`/`LessOperation`/`ScssOperation`/`JsOperation`/`ErbOperation`/
  `XmlOperation`), each backing its own `POST /api/v1/dev-utils/{css,less,scss,js,erb,xml}/beautify`
  endpoint — all reusing `MinifiableTextRequest`/`DevUtilResponse`, the same shape every existing
  operation already shares (raw text in, a minify flag, transformed text out).**
  - **`CssOperation`/`LessOperation`/`ScssOperation`/`JsOperation` all delegate entirely to a new
    shared `service/impl/support/CurlyBraceFormatter`** (`beautify(String)`/`minify(String)`,
    static utility, not itself a `DevUtilOperation`) — a lenient, brace/semicolon-driven textual
    reformatter, not a real per-language grammar parser. There's no single grammar a Java library
    could parse across CSS/LESS/SCSS/JS uniformly (LESS/SCSS extend CSS with variables/nesting/
    mixins a strict CSS parser rejects; JS has its own grammar entirely) — building four real
    parsers is a fundamentally bigger undertaking (that's what Prettier/Terser/UglifyJS actually
    do). Instead it tracks only what all four "curly-brace languages" share structurally:
    brace-nesting depth, statement-ending semicolons, and comment/string literals kept atomic so
    their contents are never touched. Same "lenient, no invalid-input failure path" trade-off
    `HtmlBeautifyOperation` already makes for HTML — **never throws**, so none of these four
    operations have a matching `DevUtilsErrorCode`. `LessOperation`/`ScssOperation` only
    *reformat* — they do not compile LESS/SCSS to plain CSS; each language's own extensions
    (`@width`/`$width` variables, `&` nesting, `@mixin`/`@include`) pass through as literal text,
    exactly as written.
    - **Known, documented limitation: JavaScript's Automatic Semicolon Insertion (ASI).** A textual
      reformatter with no real JS parser can't know that `return\nx;` means `return; x;` (the
      restricted-production rule after `return`/`break`/`continue`/`throw`) — collapsing that line
      break into a space or nothing would silently change what the code returns. Both
      `beautify`/`minify` guard against this the same way: a real line break between two ordinary
      (non-punctuation) characters is always preserved as an actual newline, never collapsed to a
      space or dropped entirely — this doesn't require recognizing the ASI-restricted keywords by
      name, it just never removes a line break where doing so could be semantically significant.
      Cost: `minify` doesn't guarantee single-line output for JS the way it mostly does for
      CSS/LESS/SCSS (whose declarations are semicolon/brace-delimited at nearly every whitespace
      boundary already, so most of their whitespace sits next to a safely-droppable punctuation
      character regardless).
    - Also deliberate: `beautify` never normalizes spacing around a bare `:` (e.g. `color:red`
      stays exactly as written, never becomes `color: red`) — a blanket "always insert a space
      after `:`" rule would corrupt a CSS/LESS/SCSS pseudo-class selector like `:hover`/
      `::before`, which requires *no* space between the colon and what follows; telling a
      declaration's colon apart from a selector's needs real grammar awareness this formatter
      deliberately doesn't have. `minify` does the opposite, safely: `:` is one of a small set of
      "safe to tighten" punctuation characters (alongside `; { } , ( ) [ ]`) whose surrounding
      whitespace is always droppable regardless of context.
    - Also deliberate: this formatter never invents structure the source didn't already signal via
      whitespace — an already-multi-line comma-separated selector list (`h1,\nh2 {...}`) stays
      multi-line (each original line break between ordinary characters is preserved, per the
      ASI-safety rule above), but one written on a single line (`h1,h2{...}`) is not proactively
      re-split. Blindly splitting on every comma would corrupt a function call/argument list
      (`rgba(0, 0, 0, .5)`, a JS array literal `[1,2,3]`) that also uses commas but isn't a
      selector list — telling those apart needs real grammar awareness too.
  - **`ErbOperation` reuses `HtmlBeautifyOperation`'s jsoup-based approach**, with one added step:
    every `<%...%>` tag is extracted and replaced with an opaque placeholder *before* jsoup ever
    parses the input, then restored verbatim afterward. Needed because jsoup's tokenizer treats a
    `<` not followed by `!`/`/`/an ASCII letter/`?` as plain text (per the HTML5 tokenizer spec) —
    so `<%` alone would already survive parsing — but jsoup then HTML-escapes text-node content on
    serialization (a literal `<` becomes `&lt;`), which would corrupt the tag's own delimiters on
    the way back out; protecting the whole tag as one opaque unit also means the embedded Ruby's
    own `<`/`>` (e.g. `<% if x < y %>`) never reaches jsoup's tokenizer at all, regardless of what
    it contains. **The placeholder scheme itself went through a real, test-caught fix**: control
    characters (STX/ETX) were tried first, on the assumption jsoup only escapes
    `<`/`>`/`&`/quotes — wrong, caught by an actual failing test: jsoup's own `Entities`
    serialization also escapes non-printable control codepoints as numeric character references
    (`&#x2;`, not the original byte), breaking the placeholder-matching restore step. Fixed by
    switching to a random alphanumeric marker (via `UUID`, generated fresh per call so it can't
    collide with anything a previous request produced) — plain letters/digits are never escaped by
    any HTML serializer. Like `HtmlBeautifyOperation`, this never throws.
  - **`XmlOperation` is the one operation in this batch backed by a real grammar parser (JAXP,
    built into the JDK — no new Maven dependency)**, the same "real parse, real invalid-input
    error" shape `JsonFormatOperation`/`YamlToJsonOperation` already establish — new
    `DevUtilsErrorCode.INVALID_XML` (`DEVUTILS_003`). Beautify strips whitespace-only text nodes
    from the parsed DOM (otherwise `Transformer`'s own indent mode would double up on whatever
    whitespace the source already had) then re-serializes with 2-space indent (matching this
    module's existing convention); minify does the same with indent off. A text node with real
    (non-blank) content is never touched, whitespace-only or not. Preserves (or omits) the
    `<?xml ...?>` declaration based on whether the *input* had one, rather than always adding or
    dropping it. **XXE (XML External Entity) hardening is not optional** — this is one of the
    fully public, unauthenticated endpoints in this reactor, and a `DocumentBuilderFactory` left at
    JDK defaults will happily resolve a `<!DOCTYPE>`'s external entities: a textbook injection
    vector where a malicious caller's DTD references a local file or an internal network URL and
    has it echoed back in the "beautified" output. Hardened per the OWASP XXE Prevention Cheat
    Sheet's JAXP baseline: `<!DOCTYPE>` disallowed outright (the simplest, most robust defense —
    this operation has no legitimate use for a DTD anyway), external general/parameter entities and
    external DTD loading disabled as defense in depth, and the `TransformerFactory` used to
    serialize the result has external DTD/stylesheet access disabled too. A custom, silent
    `ErrorHandler` still rethrows on error/fatal error (unchanged behavior) but stops the JDK's
    default handler from spamming stderr for what is routine, expected invalid input on a fully
    public endpoint.
- **3 more operations (`JsonToCsvOperation`/`CsvToJsonOperation`/`SqlFormatOperation`), backing
  `POST /api/v1/dev-utils/{json-to-csv,csv-to-json,sql/format}`.**
  - **`JsonToCsvOperation`/`CsvToJsonOperation`** use Jackson's `CsvMapper`
    (`jackson-dataformat-csv`, new dependency — no explicit `<version>`, resolves to `2.16.1` via
    the same Jackson BOM `jackson-dataformat-yaml` already relies on). `JsonToCsvOperation` accepts
    a JSON array of flat objects (or a single object, treated as one row); its column set is the
    **union** of every row's own field names in first-seen order, not just the first row's keys, so
    a heterogeneous array still produces one consistent header (blank cells for rows missing a
    given field); a nested object/array value is written as its own compact JSON string in the
    cell rather than flattened into further columns (CSV is inherently flat — no lossless flat
    representation exists for genuinely nested data). No `minify` — CSV has no distinct "compact"
    form (`TextRequest`, same reasoning `JsonToYamlOperation` already documents for YAML).
    `CsvToJsonOperation` reads the first row as the header (`CsvSchema.emptySchema().withHeader()`)
    and **never infers a value's type** — every cell comes back as a JSON string, deliberately
    (inferring number/boolean would be exactly the kind of surprising, silently-lossy behavior a
    generic converter should avoid, e.g. a ZIP code like `"007"` losing its leading zero); `minify`
    controls pretty vs. compact JSON output, same choice `JsonFormatOperation`/`YamlToJsonOperation`
    already apply to their own JSON output. Real failure paths for both: `JsonToCsvOperation`
    reuses `INVALID_JSON` (a genuine JSON syntax error, or valid JSON in a shape that can't become
    rows — e.g. a bare array of numbers); `CsvToJsonOperation` uses the new `INVALID_CSV`
    (`DEVUTILS_004`) for a genuine structural failure (a row with a different column count than the
    header). **A real bug caught by a failing test**: Jackson's `MappingIterator#next()` can't
    declare a checked exception (it implements `java.util.Iterator`), so a structural failure
    discovered mid-iteration surfaces as an *unchecked* `RuntimeJsonMappingException`, not the
    `IOException` a plain `try`-with-resources `close()` can still throw — without a dedicated
    catch for it, `CsvToJsonOperation`'s own "different column count" case (the exact scenario
    `INVALID_CSV` exists for) went completely uncaught, surfacing as a raw 500 instead of a clean
    `400`.
  - **`SqlFormatOperation`** delegates entirely to a new `service/impl/support/SqlFormatter` — a
    lenient, **keyword-driven** pretty-printer/minifier for SQL, not a real SQL-grammar parser
    (same "textual reformatter" trade-off `CurlyBraceFormatter` already makes for CSS/LESS/SCSS/JS,
    for a related reason: a real SQL parser would also have to commit to one specific dialect —
    MySQL/Postgres/SQL Server/Oracle all diverge — which a general-purpose formatting tool has no
    way to know in advance). Fully re-tokenizes the input (string/quoted-identifier literals and
    comments kept atomic) and rebuilds the output from scratch — unlike `CurlyBraceFormatter`, SQL
    has no ASI-style hazard, so nothing about the original whitespace needs preserving. Line breaks
    are **keyword-triggered** (`SELECT`/`FROM`/`WHERE`/`GROUP BY`/`ORDER BY`/`HAVING`/`LIMIT`/
    `OFFSET`/`INSERT INTO`/`VALUES`/`UPDATE`/`SET`/`DELETE FROM`/`UNION`/`UNION ALL`/every `JOIN`
    variant/`ON`/`AND`/`OR`), indented by live paren-nesting depth (2 spaces/level; `AND`/`OR` get
    one extra level) — a subquery's own `SELECT`/`FROM`/`WHERE` end up indented automatically, since
    indentation tracks paren depth, not which clause "owns" them. **Deliberately does not split a
    comma-separated column/value list onto one item per line** — same reasoning
    `CurlyBraceFormatter` already documents for never splitting a CSS selector list on a bare
    comma: there's no context-free way to tell a `SELECT` column list apart from a function call's
    argument list (`COUNT(a, b)`) without real parsing. **This paragraph describes the original
    design — both the case-preservation and no-comma-splitting claims here were later reversed, per
    direct request; see the sixth follow-up note further down in this section for the current
    behavior and the full reasoning for reversing each.** **Known, deliberate spacing trade-off: `(`
    never gets a leading space**, regardless of context — correct for a function call (`COUNT(*)`,
    not `COUNT (*)`), merely a different style preference for something like `VALUES(1, 2, 3)` — no
    parser-free way exists to tell "this is a function name" from "this keyword conventionally gets
    a space before its paren" apart, so this picks the rule that's never actually *wrong*. Quote
    handling is dialect-agnostic: `'...'` (strings), `"..."` (ANSI quoted identifiers), and
    `` `...` `` (MySQL quoted identifiers) are all atomic tokens tolerating *either* a doubled quote
    *or* a backslash escape, rather than committing to one dialect's actual rule. Comments —
    `-- line`, `# line` (MySQL), `/* block */` — are preserved verbatim by `beautify`, stripped by
    `minify`. Never throws — no matching `DevUtilsErrorCode`. **Two real bugs caught by failing
    tests during development**: (1) the keyword-matching branch originally emitted the *canonical
    uppercase* keyword text from its own lookup table instead of the token actually present in the
    input, silently upper-casing every recognized keyword regardless of how the caller wrote it —
    fixed by appending the original token text, using the lookup table only to decide *whether* a
    line break applies, never what to render. (2) `minify` never actually stripped comment tokens
    at all (they were tokenized correctly but never filtered out during rendering) — fixed by
    skipping any comment-shaped token when rendering in single-line mode.
- **3 more operations (`PhpToJsonOperation`/`JsonToPhpOperation`/`StringCaseOperation`), backing
  `POST /api/v1/dev-utils/{php-to-json,json-to-php,string-case/convert}`.**
  - **`PhpToJsonOperation`/`JsonToPhpOperation` are a real bidirectional PHP↔JSON converter,
    backed by two new, self-contained utilities — `service/impl/support/PhpArrayParser`
    (PHP→value tree) and `service/impl/support/PhpArrayWriter`** (value tree→PHP), no third-party
    PHP parsing library. **`PhpArrayParser` is a real, validating recursive-descent parser** (the
    same "real parse, real invalid-input error" shape `XmlOperation`/`CsvToJsonOperation` already
    establish), not a lenient reformatter — it has to fully understand the value structure to
    convert it. Supports both bracket (`[...]`) and legacy `array(...)` syntax; single-quoted
    strings honor only `\'`/`\\` as real escapes (PHP's own rule), double-quoted strings honor
    the common `\n`/`\t`/`\r`/`\"`/`\\`/`\$` sequences (deliberately **not** evaluating variable
    interpolation like `"$name"` — this is a literal text converter, not a PHP interpreter);
    `//`/`#` line comments and block comments are skipped anywhere between tokens. **Tolerates a
    full PHP snippet, not just the bare array literal** — an optional leading `<?php` tag, an
    optional `return` keyword, and an optional trailing `;`/`?>` are all skipped if present, so
    `JsonToPhpOperation`'s own output can be fed straight back into the parser unmodified
    (verified by a real round-trip test). An array with no explicit `=>` keys, or whose explicit
    keys form the exact sequence `0, 1, 2, ...` (PHP's own auto-increment keys, e.g. from a
    `var_export()` dump), becomes a JSON array; any other array becomes a JSON object with every
    key stringified. New `DevUtilsErrorCode.INVALID_PHP` (`DEVUTILS_005`) for
    `PhpToJsonOperation`'s own real failure path (`PhpArrayParser.PhpParseException`'s message
    already carries a `"(line N, column M)"` location, the same convention
    `ParsingExceptionMessages`/`XmlOperation` already establish); `JsonToPhpOperation` reuses
    `INVALID_JSON` instead — its input is JSON either way, and any valid JSON value can always
    become a PHP array/scalar, so the only possible failure is a genuine JSON syntax error.
    `JsonToPhpOperation`'s minify mode collapses to one line with no space around `=>`/after a
    comma, the same "minimal necessary whitespace" style `JsonFormatOperation`'s own compact
    writer already uses.
  - **`StringCaseOperation` is the one operation in this batch whose output is genuinely richer
    than a single string** — a new `dto/StringCaseResponse` (camelCase/pascalCase/snakeCase/
    kebabCase/constantCase/titleCase/sentenceCase, all at once), exactly the scenario
    `DevUtilResponse`'s own Javadoc anticipated for a future operation like this. Reuses
    `TextRequest` (no minify concept — there's no "compact form" of a case conversion). Delegates
    to a new `service/impl/support/StringCaseConverter` — splits input into words using the
    standard two-part heuristic most case-conversion tools use (insert a boundary between a
    lowercase-or-digit and a following uppercase letter, and between the last letter of an
    uppercase run and a following capitalized word, e.g. `XMLHttpRequest` → `XML`, `Http`,
    `Request`), so both delimiter-separated input (spaces/underscores/hyphens/punctuation, all
    normalized to one boundary) and already-cased input (camelCase/snake_case/etc.) split
    correctly — verified by a round-trip test confirming every case variant re-splits back into
    the same words. A pure text transform with no notion of "invalid" input — never throws, no
    matching `DevUtilsErrorCode`.
- `dto/DevUtilsLimits` — one shared `MAX_INPUT_LENGTH` constant (`100_000` characters, a
  deliberately generous but finite first-version bound), referenced by both request DTOs' `@Size`
  constraint below. This is the one fully public, unauthenticated endpoint in the reactor — an
  unbounded `input` would be a real resource-exhaustion vector (a large body fully buffered/parsed
  before any other check runs) — so every operation shares one cap rather than each endpoint
  guessing its own number; split it per operation later if a real use case needs a different bound
  for one of them.
- `dto/{MinifiableTextRequest,TextRequest,DevUtilResponse,StringCaseResponse}` — request/response
  DTOs are shared **only where the shape genuinely matches**: `MinifiableTextRequest`
  (`input`/`minify`) backs every operation with a real minify concept (`json/format`/
  `yaml-to-json`/`html/beautify`/`css/beautify`/`less/beautify`/`scss/beautify`/`js/beautify`/
  `erb/beautify`/`xml/beautify`/`csv-to-json`/`sql/format`/`php-to-json`/`json-to-php`);
  `TextRequest` (`input` only) backs `json-to-yaml`/`json-to-csv`/`string-case/convert`, none of
  which has a minify concept at all (YAML/CSV/a case conversion all lack a distinct "compact" form
  to toggle) — not the same type with an ignored field. Every `input` field carries
  `@NotBlank @Size(max = DevUtilsLimits.MAX_INPUT_LENGTH)`. `DevUtilResponse` (`output`) stays
  shared across every single-string-output operation, but `StringCaseResponse` is the first
  operation whose output is genuinely richer (7 named case variants at once) to actually need its
  own response type instead — exactly the scenario `DevUtilResponse`'s own Javadoc anticipated. See
  `DevUtilOperation`'s own Javadoc for the full reasoning against one shared request/response pair.
- `api/DevUtilsApi` (+ `api/impl/DevUtilsController`) — `POST /api/v1/dev-utils/json/format`,
  `/yaml-to-json`, `/json-to-yaml`, `/html/beautify`, `/css/beautify`, `/less/beautify`,
  `/scss/beautify`, `/js/beautify`, `/erb/beautify`, `/xml/beautify`, `/json-to-csv`,
  `/csv-to-json`, `/sql/format`, `/php-to-json`, `/json-to-php`, `/string-case/convert`,
  `/base64/encode`, `/base64/decode`, `/url/encode`, `/url/decode`, `/html-entity/encode`,
  `/html-entity/decode`. The
  controller injects each operation by its concrete type rather than dispatching through an
  enum-keyed registry — with one fixed REST endpoint per operation, there's no runtime "which
  operation" decision left to make (see `DevUtilOperation`'s own Javadoc).

**Code-quality analysis pass (mirroring the earlier `gui` dev-utils analysis) found and fixed 2
real bugs, plus duplication/doc-drift/Javadoc-coverage issues — all implemented in one pass, per
direct request.**

- **Real bug: `CurlyBraceFormatter` mishandled an unquoted `url(http://...)` argument** — a very
  common CSS pattern (`background: url(http://example.com/x.png);`). Its comment scanner had no
  awareness of "inside a `url()` argument" and misread the `//` after the scheme colon as a line
  comment start — the quote-literal handling only protected `'...'`/`"..."`/`` `...` ``, and a raw
  `url(...)` value is conventionally unquoted. In `beautify` this broke brace-depth tracking for
  everything after it (a trailing `}` swallowed into the "comment" never decremented `depth`); in
  `minify` — worse — a single-line declaration has no `\n` to stop the scan, so everything from the
  `//` to the end of the input was silently **discarded**, not just mis-formatted. Fixed by treating
  an unquoted `url(...)` argument as one atomic span (new `isUrlFunctionStart`/`scanUrlFunctionArg`
  helpers), the same protection a quoted string literal already had — a quoted `url("...")` is left
  untouched, since the existing string-literal handling already covers it. 3 new regression tests in
  `CurlyBraceFormatterTest`.
- **Real bug: `PhpArrayParser.parseNumber()` could throw an uncaught `NumberFormatException`** —
  an integer literal wider than a `long` (22+ digits) or an incomplete exponent (e.g. `1e`, where
  `consumeDigits()` is a no-op with nothing left to consume) both reach `Long.parseLong`/
  `Double.parseDouble` unguarded. Neither exception is a `PhpParseException`, so both skipped
  `PhpToJsonOperation`'s own catch clause entirely and surfaced as a generic `500` instead of the
  clean `400`/`INVALID_PHP` this parser exists to produce for exactly this class of malformed
  input — the same "an unanticipated unchecked exception slips past a narrower catch clause on a
  fully public endpoint" shape this module has now hit and fixed three times (see the
  `BusinessException` varargs-template fix and the CSV `RuntimeJsonMappingException` fix
  documented above/below). Fixed by wrapping the two parse calls in a try/catch, rethrown via the
  same `errorAt(...)` helper every other failure path in this class already uses. 2 new regression
  tests in `PhpArrayParserTest`.
- **New `service/impl/support/JsonNodeIo`** — two static helpers, `readTree`/`write`, factoring out
  a pair of blocks that had been copy-pasted near-verbatim across `JsonFormatOperation`,
  `YamlToJsonOperation`, `JsonToYamlOperation` (its read half only — its write half calls
  `yamlMapper.writeValueAsString` directly, which has no pretty/minify choice to factor out),
  `JsonToCsvOperation`, `JsonToPhpOperation`, `CsvToJsonOperation`, and `PhpToJsonOperation`:
  "parse JSON (or YAML, via a `YAMLMapper` — itself an `ObjectMapper` subtype), and on failure throw
  a `BusinessException` carrying `ParsingExceptionMessages`'s own cleaned-up message" and
  "serialize either pretty-printed or, with `minify`, compact/single-line." Roughly 8 duplicated
  blocks across 7 classes, removed with each caller still owning its own error code (and, for
  `readTree`, whatever it does with the parsed tree afterward) — this doesn't force operations with
  genuinely different shapes through one common method the way `DevUtilOperation`'s own Javadoc
  warns against for the operations themselves; it only removes literal Jackson call-and-catch
  boilerplate every caller already did identically. No dedicated `JsonNodeIoTest` — its behavior
  (pretty/minify output, malformed-input rejection) is already exercised by every one of the 7
  operations' own existing test classes, which now exercise it indirectly rather than duplicating
  those same assertions a second time against the helper directly.
- **Doc drift fixed in 4 files** that still described this module at its original 3-4-operation
  size: `dto.MinifiableTextRequest`/`dto.TextRequest` (both claimed to be shared by an operation
  list that had since grown well past what was named — `MinifiableTextRequest`'s own Javadoc said
  "exactly" 3 operations when it backs 13 today), `DevUtilsServiceApplication`'s class Javadoc
  (still listed only JSON format/YAML↔JSON/HTML beautify), and `dto.DevUtilResponse` (described
  `StringCaseResponse`'s multi-value-response shape as hypothetical future work when it already
  exists today) — the exact "comment drift" pattern root `CLAUDE.md` already calls out as having
  bitten this project before.
- **Every one of the 16 operations' `execute(...)` methods, plus
  `exception.ParsingExceptionMessages#friendlyMessage`, gained method-level Javadoc** — per root
  `CLAUDE.md`'s own rule ("Javadoc for every … public method"), which this module's otherwise
  thorough class-level Javadoc had drifted away from at the method level across every operation.
  Each documents its real `@throws BusinessException` failure path where one exists, or states
  explicitly that it never throws (mirroring each class's own Javadoc) where one doesn't.

**Follow-up bug: `JsonNodeIo.write`'s pretty-print output diverged from conventional JSON
formatting, reported directly against a real payload (`"key" : value` — a space on both sides of
the colon; an array's elements on one single line, space-padded, e.g. `[ "a", "b" ]`; an empty
array rendered `[ ]` instead of `[]`) — new `service/impl/support/ConventionalJsonPrettyPrinter`
fixes all of it, plus a 4th bug caught only while verifying the fix.** `ObjectMapper#writerWithDefaultPrettyPrinter()`
(what `JsonNodeIo.write`'s pretty branch used before this) is Jackson's own `DefaultPrettyPrinter`
at its stock defaults — genuinely different from what every mainstream JSON formatter (VS Code's
own, most "JSON beautifier" sites, `JSON.stringify(value, null, 2)`) produces, not a style
preference. `ConventionalJsonPrettyPrinter` overrides `writeObjectFieldValueSeparator` (`": "`, not
`" : "`), sets the *array* indenter to the same multi-line `DefaultIndenter` objects already use
(Jackson's own default only multi-line-indents objects; arrays use the single-line
`FixedSpaceIndenter`), and overrides `writeEndArray`/`writeEndObject` to drop the one
padding-space `DefaultPrettyPrinter` still writes for an empty container even under a multi-line
indenter (`[ ]`/`{ }` → `[]`/`{}`). **A 4th bug, caught only by measuring the first attempt's real
output byte-for-byte (a small standalone Java harness against the actual resolved
`jackson-core:2.19.2` jar) rather than trusting a test-failure diff**: the first cut left the
default `DefaultIndenter.SYSTEM_LINEFEED_INSTANCE` in place, whose line ending is
`System.lineSeparator()` — CRLF on the Windows dev machine this was built on, LF wherever this
service actually deploys (Linux Docker). That silently made output platform-dependent — identical
input could produce a byte-for-byte different response depending on which OS ran the JVM — and,
worse, the CRLF-vs-LF mismatch against the (LF-only) test literals *looked* exactly like a doubled-
indentation bug in the AssertJ diff output (a raw `\r` right before an inserted `\n` renders as a
carriage return in a terminal, visually shifting every subsequent line), which is what the harness
was built to rule out before accepting a wrong diagnosis. Fixed by constructing both indenters
explicitly as `new DefaultIndenter("  ", "\n")` — a fixed `\n`, never system-dependent. Used by
`JsonNodeIo.write` in place of `writerWithDefaultPrettyPrinter()`, so every operation that goes
through it (JSON Format, YAML→JSON, CSV→JSON, PHP→JSON) gets the fix at once. 5 new tests in a
dedicated `ConventionalJsonPrettyPrinterTest` (the one support class in this module that got its
own test file after all, once it grew real, non-delegating logic worth testing directly) plus a
tightened assertion in `JsonFormatOperationTest` locking in the exact expected byte sequence.

**Second follow-up bug, same shape, reported directly against a real payload — `JsonToYamlOperation`'s
own YAML output diverged from conventional YAML formatting too** (a leading `---` document-start
marker; every string quoted regardless of need, e.g. `"Vui Coding"`; a block sequence's `-`
indicator rendered at the *same* column as its parent key instead of indented under it, e.g.
`features:\n- tools`). Root cause was `config/YamlMapperConfig`'s `YAMLMapper.builder().build()`
call — Jackson's own stock `YAMLGenerator.Feature` defaults, same "genuinely diverges from every
mainstream formatter, not a style preference" bug class `ConventionalJsonPrettyPrinter` fixes on
the JSON side. Fixed with 3 builder overrides on that same bean, verified against the exact
reported input via a standalone Java harness before landing (same discipline the JSON pretty-print
fix's own byte-for-byte verification established, after that fix's own near-miss with a misleading
diff): `WRITE_DOC_START_MARKER` disabled (no `---` for a single, standalone document — this
operation never emits a multi-document stream); `MINIMIZE_QUOTES` enabled (Jackson quotes every
string scalar by default for round-trip type-fidelity safety — an unquoted `true`/`123` could
parse back as a boolean/number instead of a string — but that safety only matters for a value that
actually needs it; a plain string like `Vui Coding` never did); `INDENT_ARRAYS_WITH_INDICATOR`
enabled, **not** the plainer-sounding `INDENT_ARRAYS` — tried first and rejected once actually
measured, since it only indents the `-` indicator by 1 space (`features:\n - tools`), not the
conventional 2-space block indent (`features:\n  - tools`) every mainstream YAML formatter
produces; `INDENT_ARRAYS_WITH_INDICATOR` folds the indicator's own width into the indent
calculation instead, which does match. `YAMLGenerator.Feature.USE_PLATFORM_LINE_BREAKS` was
checked and confirmed **already** `false` by default (unlike the JSON pretty-printer's own
`DefaultIndenter.SYSTEM_LINEFEED_INSTANCE` bug) — this operation's YAML output was never
platform-dependent, so nothing needed fixing there. `JsonToYamlOperationTest`'s own `setUp()` now
builds its `yamlMapper` via `new YamlMapperConfig().yamlMapper()` instead of a second, bare
`YAMLMapper.builder().build()` call, so the test can never silently drift from the real bean's own
configuration the way it had (undetected, since the existing round-trip assertion tolerates either
formatting style) before this fix — plus a new test locking in the exact expected byte sequence
against the reported input. `YamlToJsonOperation` (the read direction) is unaffected — YAML*parser*
behavior, not generator/output behavior, and none of the 3 changed features are parser-side.

**Third follow-up bug, same shape, reported directly against a real CSS example — `CurlyBraceFormatter`'s
own "beautify never spaces a `:`" behavior (previously a deliberate, documented limitation, not a
bug) genuinely was fixable after all, plus a real, separate gap in blank-line separation between
rules.** The original limitation existed because a blanket "always space after `:`" rule would
corrupt a pseudo-class selector like `.card:hover`/`&:hover`, and telling a declaration's colon
apart from a selector's *seemed* to need real grammar awareness this deliberately-lenient formatter
doesn't have — reconsidered once the reported example (`display:grid` wanting a space,
`.card:hover` not) made clear the two are locally distinguishable with two bounded checks, not a
full parser:
- A running `parenDepth` counter (mirrors the existing brace `depth` counter, but for `(`/`)`) —
  a colon already inside an open paren (a media feature, `@media (min-width: 768px)`; an SCSS map
  key) is always declaration-style, regardless of what follows.
- Otherwise, new `selectorFollowsBeforeStatementEnd` looks forward from the colon (skipping any
  intervening string literal, comment, or unquoted `url(...)` argument, atomic the same way the
  main scan already treats them) for whichever of `{`/`;`/`}` comes first — a `{` means a nested
  rule's own selector (`&:hover {`, no space); `;`/`}`/end of input means a declaration
  (`color:red;`, gets a space).

When a space is added, any existing run of spaces/tabs right after the colon is collapsed first,
so `color:red`/`color: red`/`color:   red` all normalize to exactly one space rather than doubling
up — `minify` needed no matching change, since `:` was already one of its own `SAFE_BOUNDARY_CHARS`
and already strips all surrounding whitespace unconditionally in both contexts. **Known remaining
imprecision, accepted rather than chased further**: a pseudo-class function argument containing an
unescaped `{`/`;`/`}` (not legal in real CSS) would still be misread — hasn't come up in practice,
and chasing it further would mean real paren-aware tokenization, past what this lenient formatter
is meant to be. **Separately, a blank line is now inserted after a `}` that closes a rule back down
to brace-nesting depth `0`** (a genuinely top-level rule, not a nested one) — `.a {...}` immediately
followed by `.a:hover {...}` used to render with no visual break between them; a trailing blank
line at the very end of input is harmless, trimmed by this method's own final `.strip()`. Both
fixes are `beautify`-only (shared by `CssOperation`/`LessOperation`/`ScssOperation`/`JsOperation`,
so both apply uniformly across all four "curly-brace languages" this formatter serves, per its own
"one shared reformatter, not four near-identical copies" design — not special-cased per operation).
5 new tests in `CurlyBraceFormatterTest` (the exact reported example, a nested-selector case, a
media-feature-colon case, an existing-spacing-normalization case, and a plain blank-line case),
plus 2 existing tests (`indentsNestedBlocksByDefault`,
`beautifyDoesNotTreatDoubleSlashInsideUnquotedUrlAsALineComment`) and one end-to-end
`DevUtilsServiceApplicationTests` assertion updated from an unspaced-colon expectation to a spaced
one.

**Fourth follow-up bug, same shape, reported directly against a real LESS example — the
blank-line-between-top-level-rules fix above turned out to be too narrow (missed the identical gap
one level deeper), and a separate, previously-unhandled comma-spacing gap surfaced in the same
report.** `.button { background: @brand; &:hover {...} }` — a plain declaration followed by a
nested rule *inside* a block — used to render with no blank line between them, the same problem the
earlier top-level-only fix solved but didn't generalize. Both are the same underlying rule, not two
separate mechanisms: `beautify` now tracks, per brace-nesting depth, whether the block currently at
that depth has already emitted a statement or nested rule of its own (`hasContentAtDepth`, pushed/
popped alongside `{`/`}`) — a blank line goes in before any line that itself opens a nested rule,
whenever the block it belongs to already had prior content, whether that block is the whole
document's own top level or a rule nested many levels deep. New `service.impl.support
.CurlyBraceFormatter#beginLine` is the actual mechanism (called from every one of the method's
`atLineStart` branches); the old, narrower "blank line after a `}` reaching depth 0" logic was
removed outright, superseded by this general rule (verified it produces byte-identical output for
the original top-level-only report before removing the old code path). **A real bug caught in the
same pass, by an existing test failing**: the first cut of `beginLine` used `atLineStart` alone to
decide whether a "new statement" had begun — wrong, since an already-multiline selector list (`h1,
\nh2 {...}`) also sets `atLineStart` true on its *second* line, which isn't a new statement at all,
just a preserved line-wrap of the first. `preservesAlreadyMultilineSelectorLists` caught this
immediately (a spurious blank line appeared between `h1,` and `h2 {`) — fixed with a second,
narrower flag, `atStatementStart` (true only when set by `;`/`{`/`}`, left untouched by the
whitespace branch's own mid-statement newline handling), so `beginLine`'s blank-line decision only
ever fires once per genuine statement, never on a line-wrap resumption of one already in progress.
**Separately, `beautify` now normalizes the same-line spacing after a `,`** — `darken(@brand,10%)`
needed a space too (`darken(@brand, 10%)`), the other half of the same LESS report. Unlike the
colon fix, this needed no context check at all: a space after `,` is unconditionally correct
whether it's a function argument list, a selector list, or a JS array/object literal — skipped only
when a newline already follows (preserves an already-multiline list's own line breaks) or a closing
`)`/`]`/`}` immediately follows (no stray space padding a trailing comma away from its closer).
Explicitly doesn't conflict with this class's own "never invents line-break structure" rule — that
rule is about not *splitting* onto new lines, and this only ever inserts a same-line space, never a
newline. 3 new tests in `CurlyBraceFormatterTest` (the exact reported LESS example, a
comma-in-selector-list-and-function-call case, and a trailing-comma-before-a-closing-bracket case),
plus a fortified `preservesAlreadyMultilineSelectorLists` (now documents the exact regression it
guards) and an updated `indentsNestedBlocksByDefault` (now expects the blank line its own nested
`.b` rule should get, per this fix).

**Fifth follow-up bug, two real issues reported together against JSON↔CSV: an overly-conservative
default quoting rule in `JsonToCsvOperation`, plus a scoped, direct-request change to
`CsvToJsonOperation`'s own "never guess a cell's type" rule.**
- **`JsonToCsvOperation` silently quoted a value for containing nothing more than a plain space**
  (e.g. `JSON Formatter` → `"JSON Formatter"`) — found by decompiling Jackson's own
  `CsvEncoder` (no sources jar available for this Jackson module) rather than guessing:
  `CsvMapper`'s default ("loose") quoting check quotes any cell value containing *any* character
  below ASCII 45 (comma, the default separator, plus one) — a deliberately conservative Jackson
  default, not what RFC 4180 actually requires (the separator, the quote character, or a line
  break). Fixed by building the `CsvMapper` with `CsvGenerator.Feature.STRICT_CHECK_FOR_QUOTING`
  enabled — Jackson's own "strict" check, which quotes only when RFC 4180 requires it. Verified via
  a standalone harness against the actual resolved `jackson-dataformat-csv:2.19.2` jar, confirming
  both halves: a plain-space value now stays unquoted, and a value that genuinely contains a
  quote/comma/newline is still correctly quoted (with the internal quote doubled, per RFC 4180).
- **`CsvToJsonOperation` gained one deliberately narrow exception to its own "every value comes
  back as a string" design** — a cell that's exactly `true`/`false` (case-insensitive) now becomes
  a real JSON boolean; every other value, numbers included, still stays a string exactly as before.
  This was a real design question, not an obvious bug fix — the original "never guess a type" rule
  has a concrete, still-valid reason behind it (a ZIP code like `"007"` would silently lose its
  leading zero if reinterpreted as a number), so **the scope was confirmed with the user before
  implementing** (three options presented: booleans only, booleans + numbers, or leave as-is) —
  booleans-only was chosen specifically because it's the one type with no such ambiguity: there's
  no legitimate CSV convention where the literal text `true`/`false` needs to survive as a *string*
  with some other meaning, unlike a number. `parseCsv` now reads every cell as a `String` (Jackson's
  CSV reader has no other option — a CSV cell is text by definition) and post-processes each one
  through a new `toCellValue` (the one place the boolean-recognition rule lives, not duplicated per
  row) before handing `List<Map<String, Object>>` (was `Map<String, String>`) to `JsonNodeIo.write`
  — a `Boolean` value serializes as a bare JSON `true`/`false` automatically, no writer-side change
  needed. 4 new tests total across both operations (a plain-space-value case and a
  genuinely-needs-quoting case for `JsonToCsvOperation`; a mixed-case-boolean-vs-similar-string case
  and the exact reported round-trip example for `CsvToJsonOperation`), plus the existing
  `everyValueComesBackAsAJsonStringNeverAnInferredNumber` test left unchanged and still passing
  (confirming numeric strings are genuinely untouched by this fix).

**Sixth follow-up — reported directly against a real SQL example, and, unlike every prior fix in
this section, this one reversed two previously *deliberate* `SqlFormatter` design choices rather
than fixing an oversight: keyword case is no longer preserved verbatim (every recognized keyword
now renders uppercase), and a comma-separated list now does split one item per line.** Given the
size — reversing a previously-fixed, tested case-preservation bug and an explicitly-documented
"never splits a list" rule, in the same request — the scope was confirmed with the user before
implementing (four options presented: the full expected style, uppercase-only, list-splitting-only,
or leave as-is); "full expected style" was chosen. Three keyword roles now exist, each in its own
list, replacing the single flat `LINE_BREAK_KEYWORDS` list this section's own paragraph above still
describes:
- **`TOP_LEVEL_CLAUSE`** (`SELECT`/`FROM`/`WHERE`/`GROUP BY`/`ORDER BY`/`HAVING`/`LIMIT`/`OFFSET`/
  `INSERT INTO`/`VALUES`/`UPDATE`/`SET`/`DELETE FROM`/`UNION`/`UNION ALL`) — starts its own fresh
  line at the current paren depth, same as before, but now its own body (everything until the next
  recognized keyword) *also* starts on a further-indented fresh line of its own, rather than staying
  inline with the clause keyword the way it always used to.
- **`BODY_BREAK`** (every `JOIN` variant, `AND`, `OR`) — starts a fresh line too, but *within* the
  current clause's own body indent (one level deeper than the clause keyword), not a brand-new
  top-level line — this is what keeps `LEFT JOIN posts p ON p.user_id = u.id` aligned with `FROM`'s
  other body lines instead of resetting to column 0 the way it used to. **`AND`/`OR` deliberately
  lost their own extra indent level in the same pass** — now sharing the plain body-level indent
  `JOIN` gets, not one level deeper than that: once a clause's first condition moved onto its own
  indented body line (rather than staying inline with the clause keyword), giving `AND`/`OR` their
  own further indent on top of that stopped reading as "conditions under WHERE" and started reading
  as an arbitrary extra nesting level — this wasn't explicitly requested, but was the natural,
  consistent generalization once `JOIN`'s own alignment was pinned down by the reported example.
- **`INLINE`** (`ON`, `AS`, `ASC`, `DESC`, `TRUE`, `FALSE`, `NULL`, `NOT`, `IN`, `LIKE`, `IS`,
  `BETWEEN`, `EXISTS`, `DISTINCT`) — rendered uppercase but never breaks a line on its own; this is
  what keeps `ON p.user_id = u.id` attached to its own `JOIN` line instead of wrapping onto its own
  third line the way `ON`'s old membership in the flat `LINE_BREAK_KEYWORDS` list used to force. A
  deliberately bounded, non-exhaustive list, not full dialect-aware reserved-word coverage — a
  function name that happens to also be a common SQL built-in (`COUNT`/`SUM`/`AVG`/...) is
  deliberately *not* in any of the three lists, since it's an identifier, not a keyword, and there's
  no reliable parser-free way to tell "this word is a function name" from "this word happens to
  also be a keyword" apart in general.

**Comma-splitting is scoped to the current clause's own base paren depth, not blind** — a comma
triggers a line break (trailing comma on the line before it) only once live paren depth has
returned to exactly the depth the *current* clause itself started at, so a comma inside a function
call's own argument list (`count(id, other)`) or a subquery — sitting at a *deeper* paren depth —
stays correctly inline rather than corrupting the call/subquery. This is more tractable for SQL
than the identically-shaped problem `CurlyBraceFormatter` still declines to solve for CSS selector
lists (see this file's own note on that class) — `SqlFormatter` already had to track paren depth
for indentation, and a SQL list's own commas are reliably at the clause's own base depth in a way a
bare CSS selector list isn't. `matchKeywordPhrase` was tightened in the same pass to always prefer
the *longest* matching phrase at a position (`LEFT OUTER JOIN` over `LEFT JOIN`, `UNION ALL` over
`UNION`) regardless of any one list's own declaration order — the previous version relied on
manually keeping longer phrases listed before their own shorter prefixes across one flat list,
which happened to already be correct but wasn't actually guaranteed by anything once phrases split
across three separate lists.

**Known, accepted imprecision, not chased further**: a scalar subquery appearing *inside* a
`SELECT` list item (e.g. `SELECT a, (SELECT COUNT(*) FROM y) AS cnt`) still has its own nested
`SELECT`/`FROM` broken onto their own fresh lines the same as any other subquery, which leaves the
opening `(` alone on its own line with nothing else on it (the nested `SELECT` that immediately
follows forces its own fresh line too) — reads unusually for what's really one scalar expression,
but doesn't corrupt the SQL text, only this one nested shape's visual layout. Solving it generally
would mean distinguishing "a clause keyword genuinely starting a new top-level statement" from "a
clause keyword sitting inside what's really just one scalar expression" — real parsing, past what
this lenient formatter is meant to be.

10 new/rewritten tests in `SqlFormatterTest` (the exact reported example; keyword/literal
uppercasing that confirms `count`/function names stay untouched; `AND`/`OR`'s new shared indent
level; the subquery case, now documenting its own known imprecision instead of asserting the old
layout; the multi-word `JOIN`/`ON`-stays-inline case; the longest-match-wins guarantee; the
paren-depth-scoped comma split, including the function-argument-comma exclusion; minify's own
uppercase-but-never-split behavior) plus two stale lowercase-keyword assertions fixed in
`SqlFormatOperationTest` and `DevUtilsServiceApplicationTests`. Test suite grew from 157 to 161.

**Seventh follow-up, a small one, reported directly against a real payload — `PhpArrayWriter#write`'s
own non-minify output was missing a blank line between `<?php` and `return`**, the
standard convention this snippet's own shape is meant to evoke (most PHP file/snippet generators,
and PSR-12-influenced style guides, leave a blank line after the opening tag before the first real
statement). Fixed by writing `"<?php\n\nreturn "` instead of `"<?php\nreturn "` — `minify`'s own
single-line output is untouched, since there's no "blank line" concept once everything collapses
onto one line. `PhpArrayParser` needed no change — it already tolerates arbitrary whitespace between
tokens, so the extra blank line round-trips correctly with no parser-side work. 3 existing exact-match
tests updated in `PhpArrayWriterTest` (including the empty-array/-object case) plus the one in
`JsonToPhpOperationTest` that is the exact reported example. Test suite count unchanged (161) — every
affected assertion already existed and just needed its expected string updated, no new test added
given `JsonToPhpOperationTest`'s own existing test already exercises this exact scenario precisely.

**Eighth follow-up — 2 new operations (`Base64EncodeOperation`/`Base64DecodeOperation`), the first
to declare `OperationGroup.ENCODERS_DECODERS` instead of `FORMATTERS`**, per direct request:
"Base64 String - Encode and decode Base64 strings." Backs `POST /api/v1/dev-utils/base64/encode`
and `POST /api/v1/dev-utils/base64/decode`. Both use the JDK's own `java.util.Base64` with the
standard (`+`/`/`) alphabet, not URL-safe (`-`/`_`) — matching what "Base64 string" conventionally
means, and what most general-purpose Base64 tools default to — and both read/write text as UTF-8
(`StandardCharsets.UTF_8`) explicitly rather than relying on the JVM's platform-default charset, a
real trap this module has now hit and worked around twice: verifying `Base64EncodeOperation`
against a genuinely multi-byte UTF-8 input via a real standalone Java harness first caught the
harness itself silently mis-encoding the *source file's own literal characters* on Windows, since
`javac` defaults to the platform charset rather than UTF-8 unless told otherwise (`-encoding UTF-8`
on the compiler, `-Dfile.encoding=UTF-8` at runtime) — nothing to do with the actual
`Base64EncodeOperation` code, which was correct the whole time, but a reminder that a harness
itself can be the thing lying to you rather than the code under test (the same "misleading artifact
vs. real bug" distinction this module's own CRLF-vs-LF pretty-printer fix ran into earlier). The
original reported/verified example used Vietnamese diacritics; the module's own test data was
later switched to an English sentence plus an emoji (still genuinely multi-byte UTF-8 via the
emoji's own 4-byte encoding, without reaching for non-English script) per a direct follow-up
request — `encode("Hello from Vui Coding 👋")` equals `"SGVsbG8gZnJvbSBWdWkgQ29kaW5nIPCfkYs="`,
re-verified against the same harness (this time also checking the source file's own
`String#codePoints()` explicitly, to rule out a silent character substitution rather than just
trusting a round-trip check, which would pass trivially for *any* correctly round-tripped string
regardless of whether it's the intended one).
- `Base64EncodeOperation` never throws — every string, however unusual, has a valid Base64
  encoding — so, like `StringCaseOperation`, it has no matching `DevUtilsErrorCode`.
- `Base64DecodeOperation` is a real "parse, real invalid-input error" operation, the same shape
  `XmlOperation`/`PhpToJsonOperation` already establish — new `DevUtilsErrorCode.INVALID_BASE64`
  (`DEVUTILS_006`), backed directly by `Base64.getDecoder()`'s own `IllegalArgumentException`
  message (terse but already specific enough not to need rewording, e.g. "Illegal base64
  character 20"). `input` is `String#strip()`ped before decoding (a leading/trailing newline is a
  common artifact of pasting a Base64 string from elsewhere, and shouldn't fail an otherwise-valid
  decode) but the decoder itself stays the strict `Base64.getDecoder()`, not the lenient
  `Base64.getMimeDecoder()` — deliberately: the MIME decoder silently ignores any character
  outside the Base64 alphabet anywhere in the input, which risks quietly "fixing" genuinely
  malformed input instead of reporting it, the same "never guess, report real errors" posture this
  module's other real-parser-backed operations already follow. A successfully decoded byte
  sequence that isn't itself valid UTF-8 text is *not* a separate failure case — `new String(bytes,
  UTF_8)`'s own standard Java behavior (replace invalid sequences with U+FFFD) applies as-is; this
  operation doesn't add stricter validation on top of it, since a Base64 payload isn't guaranteed
  to be UTF-8 text in the first place (arbitrary binary data, or text in another encoding, are both
  legitimate things to have Base64-encoded).
- **`gui`'s own `/dev-utils` page needed a real, if small, new capability to support this
  one** — every prior operation has exactly one action button; Base64 needed two (Encode, Decode)
  over the same input, run independently. `DevUtilToolPanel.tsx`'s `OperationConfig` gained an
  optional `secondaryAction: { label, onSubmit }`, rendered as a second `SubmitButton` next to the
  primary one when present; a new `savingAction: 'primary' | 'secondary' | null` (replacing the old
  plain `saving` boolean) tracks which one is in flight, disabling both while either is submitting
  rather than allowing an overlapping double-submit against the same shared `input`/`output` state.
  Deliberately not generalized into an arbitrary-length actions array — every operation today needs
  either one action or exactly two, so this was built for two concrete buttons, not a hypothetical
  N. See `gui/CLAUDE.md`'s own dev-utils section for the full GUI-side detail.
- 11 new tests: `Base64EncodeOperationTest` (3 — plain ASCII, the exact reported multi-byte
  example, empty string), `Base64DecodeOperationTest` (5 — plain ASCII, the exact reported example
  the other direction, a leading/trailing-newline tolerance case, empty string, and the
  malformed-input `BusinessException`/`INVALID_BASE64` case), plus 3 new
  `DevUtilsServiceApplicationTests` cases (both new endpoints' reachability with no
  `Authorization` header, and malformed Base64 returning `400` with `DEVUTILS_006` through the
  shared `GlobalExceptionHandler`). Test suite grew from 161 to 172, verified via a real
  `mvn -pl dev-utils-service -am test` run (JDK 21).

**Ninth follow-up — 2 more operations (`UrlEncodeOperation`/`UrlDecodeOperation`), the second pair
to declare `OperationGroup.ENCODERS_DECODERS`**, per direct request: "Implement new operation in
ENCODERS_DECODERS group: URL Encode / Decode - URL encoding and decoding." Backs
`POST /api/v1/dev-utils/url/encode` and `POST /api/v1/dev-utils/url/decode` — same Encode/Decode
two-button shape `Base64EncodeOperation`/`Base64DecodeOperation` already established, so no new
`gui`-side capability was needed this time (`base64-string`'s own `secondaryAction`/`savingAction`
mechanism is reused as-is).
- `UrlEncodeOperation` delegates to the JDK's own `URLEncoder.encode(input, UTF_8)`, then corrects
  its one divergence from conventional URL percent-encoding: that encoder represents a space as
  `+` (it targets HTML form submission, `application/x-www-form-urlencoded`), not `%20` — fixed
  with a trailing `.replace("+", "%20")`, matching what JavaScript's `encodeURIComponent` (and most
  general-purpose "URL Encode" tools) produce instead. Every other reserved delimiter (`: / ? # &
  =`, etc.) is left exactly as `URLEncoder` already produces it. Never throws — every string has a
  valid percent-encoding — so, like `Base64EncodeOperation`, it has no matching
  `DevUtilsErrorCode`.
- `UrlDecodeOperation` delegates to `URLDecoder.decode(input, UTF_8)`, which — matching
  `UrlEncodeOperation`'s own output, and conventional query-string decoding generally — also
  decodes a literal `+` back into a space. Real "parse, real invalid-input error" operation, the
  same shape `Base64DecodeOperation` already establishes — new
  `DevUtilsErrorCode.INVALID_URL_ENCODING` (`DEVUTILS_007`), backed directly by `URLDecoder`'s own
  `IllegalArgumentException` message (e.g. "URLDecoder: Incomplete trailing escape (%) pattern") —
  already specific enough not to need rewording.
- `gui`'s `config/operations.tsx` gained the new `url-string` entry (group/category
  `'Encoders/Decoders'`, right after `base64-string`); `api/devUtilsApi.ts` gained
  `encodeUrl`/`decodeUrl`. See `gui/CLAUDE.md`'s own dev-utils section for the matching GUI-side
  note.
- 10 new tests: `UrlEncodeOperationTest` (3 — the exact reported URL example, a space-vs-`+`
  encoding case, empty string), `UrlDecodeOperationTest` (4, one of which round-trips both `%20`
  and a literal `+` back to a space), plus 3 new `DevUtilsServiceApplicationTests` cases (both new
  endpoints' reachability with no `Authorization` header, and malformed percent-encoding returning
  `400` with `DEVUTILS_007` through the shared `GlobalExceptionHandler`). Test suite grew from 172
  to 182, verified via a real `mvn -pl dev-utils-service -am test` run (JDK 21).

**Tenth follow-up — 2 more operations (`HtmlEntityEncodeOperation`/`HtmlEntityDecodeOperation`),
the third pair to declare `OperationGroup.ENCODERS_DECODERS`**, per direct request: "HTML Entity -
HTML entity encoding and decoding." Backs `POST /api/v1/dev-utils/html-entity/encode` and
`POST /api/v1/dev-utils/html-entity/decode` — same Encode/Decode two-button shape
`Base64EncodeOperation`/`UrlEncodeOperation` already established, so no new `gui`-side capability
was needed again.
- **`HtmlEntityEncodeOperation` escapes only the five structurally-significant markup characters
  — `& < > " '` — into `&amp; &lt; &gt; &quot; &#39;`, character by character (not a sequence of
  `String#replace` calls, which would risk re-escaping a literal `&` a later replacement just
  produced).** Deliberately does **not** escape anything else, confirmed against the exact
  reported example: `©` (U+00A9) survives the round trip untouched. This was a real, deliberate
  design decision, not an oversight — Spring's own `infra`-adjacent `HtmlUtils.htmlEscape` (and
  Apache Commons Text's `escapeHtml4`) both escape the *full* ISO-8859-1/HTML4 named-entity table,
  which would have rewritten `©` to `&copy;` and contradicted the reported example outright; this
  operation is a narrower "make this text safe to embed literally inside HTML markup" transform
  instead, the same fixed five-character set XML 1.0/1.1's own predefined entities cover (matching
  what `org.apache.commons.text.StringEscapeUtils#escapeXml11` would produce, had that dependency
  already been on this module's classpath — it isn't, and wasn't added just for this, since the
  five-character-switch is a five-line hand-roll with no library needed). Never throws — every
  string has a valid escaped form — so, like `Base64EncodeOperation`/`UrlEncodeOperation`, it has
  no matching `DevUtilsErrorCode`.
- **`HtmlEntityDecodeOperation` decodes exactly the inverse set, plus two extra apostrophe
  spellings (`&apos;`, `&#x27;`) tolerated on decode even though encode only ever emits `&#39;`** —
  via one `Pattern#matcher(...).replaceAll(Function<MatchResult, String>)` pass (Java 9+'s
  function-based overload; the returned string is inserted verbatim, no backslash/`$`
  reinterpretation to guard against), not a sequence of `String#replace` calls — this is what
  makes decoding exactly single-level: `"&amp;lt;"` decodes to `"&lt;"`, never all the way back to
  `"<"`, since the one matched `&amp;` token is consumed and the scan continues *after* it, never
  re-examining the `lt;` that followed. **Deliberately lenient, no broader named-entity table
  recognized** (`&copy;`, `&nbsp;`, etc. all pass through completely untouched) — the same
  "encode/decode are exact inverses of one fixed set, not a general HTML-entity table" scoping
  choice the encode side already makes, mirrored onto decode; an unrecognized `&...;` sequence is
  left alone rather than rejected, matching how a browser's own entity decoder behaves. Never
  throws either — this module's one pair where *neither* direction has a matching
  `DevUtilsErrorCode` (see `DevUtilsErrorCode`'s own updated Javadoc).
- `gui`'s `config/operations.tsx` gained the new `html-entity-string` entry (group/category
  `'Encoders/Decoders'`, right after `url-string`, with a new `HtmlOutlined` sidebar icon);
  `api/devUtilsApi.ts` gained `encodeHtmlEntity`/`decodeHtmlEntity`.
- 11 new tests: `HtmlEntityEncodeOperationTest` (4 — the exact reported example, a non-ASCII-stays-
  untouched case, the exactly-once-ampersand-escaping case, empty string),
  `HtmlEntityDecodeOperationTest` (5 — the exact reported example, both apostrophe spellings plus
  `&amp;`, the single-level-decode case, the unrecognized-entity-passes-through case, empty
  string), plus 2 new `DevUtilsServiceApplicationTests` cases (both new endpoints' reachability
  with no `Authorization` header — neither has a malformed-input case to test, since neither
  direction throws). Test suite grew from 182 to 193, verified via a real
  `mvn -pl dev-utils-service -am test` run (JDK 21).

**Test suite:** `src/test/java/.../service/impl/` — one plain JUnit 5 test class per operation
(`JsonFormatOperationTest`, `YamlToJsonOperationTest`, `JsonToYamlOperationTest`,
`HtmlBeautifyOperationTest`, `CssOperationTest`, `LessOperationTest`, `ScssOperationTest`,
`JsOperationTest`, `ErbOperationTest`, `XmlOperationTest`, `JsonToCsvOperationTest`,
`CsvToJsonOperationTest`, `SqlFormatOperationTest`, `PhpToJsonOperationTest`,
`JsonToPhpOperationTest`, `StringCaseOperationTest`, `Base64EncodeOperationTest`,
`Base64DecodeOperationTest`, `UrlEncodeOperationTest`, `UrlDecodeOperationTest`,
`HtmlEntityEncodeOperationTest`, `HtmlEntityDecodeOperationTest`), plus `service/impl/support/
CurlyBraceFormatterTest` (the shared CSS/LESS/SCSS/JS reformatter — brace nesting, already-
multiline selector lists, comment/string-literal protection, the JS ASI-safety guarantee,
never-throws-on-unterminated-input), `service/impl/support/SqlFormatterTest` (clause-keyword line
breaks, `AND`/`OR` extra indent, subquery paren-depth indent, multi-word `JOIN`/`GROUP BY` phrase
recognition, string-literal protection including keyword-like content inside a string, never
splitting on a bare comma, minify's comment-stripping/single-line collapse, never-throws),
`service/impl/support/PhpArrayParserTest` (associative vs. list detection including the
sequential-explicit-keys case, legacy `array(...)` syntax, single- vs. double-quoted escape rules,
comment skipping, trailing commas, `PhpParseException` with a real line/column on malformed
input), `service/impl/support/PhpArrayWriterTest` (pretty vs. minified output, string escaping, a
real round-trip through `PhpArrayParser`), and `service/impl/support/StringCaseConverterTest`
(every case variant, camelCase/acronym/delimiter word-splitting, a round-trip confirming every
variant re-splits into the same words), no Mockito anywhere — each constructs real `ObjectMapper`/
`YAMLMapper`/`CsvMapper` instances rather than mocking Jackson, since the whole point is verifying
real parse/serialize behavior (pretty vs. minified output, malformed-input rejection, round-trip
structural equality via `readTree`, jsoup's lenient-parsing/indent behavior, and — for
`XmlOperation`/`CsvToJsonOperation`/`PhpToJsonOperation` — real JAXP/CSV/PHP parsing and rejection
behavior). Plus `DevUtilsServiceApplicationTests` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`
+ `@AutoConfigureMockMvc`) — boots the real Spring context and hits all twenty-two endpoints with
**no** `Authorization` header through the real filter chain, confirming end to end (not just by
static reasoning) that the app actually starts and every endpoint is genuinely public. This is
exactly the test that caught the `DataSourceAutoConfiguration` boot failure above, and it also
covers the `MAX_INPUT_LENGTH` boundary (accepted at exactly the cap, rejected one over it — the
latter caught by `@Size` before ever reaching an operation) and confirms malformed XML/CSV/PHP/
Base64/URL-encoding all return `400` with
`DEVUTILS_003`/`DEVUTILS_004`/`DEVUTILS_005`/`DEVUTILS_006`/`DEVUTILS_007` respectively through the
shared `GlobalExceptionHandler` — `html-entity/encode`/`decode` have no matching case here, since
neither direction ever throws (see `DevUtilsErrorCode`'s own updated Javadoc). Plus
`service/impl/support/
ConventionalJsonPrettyPrinterTest` (the one support class in this module with its own dedicated
test file rather than only being exercised indirectly through an operation's own tests — see that
class's own note above for why). 193 tests total (161 as of the seventh follow-up above, plus 8 new
Base64 unit tests and 3 new `DevUtilsServiceApplicationTests` cases from the eighth follow-up, 7 new
URL unit tests and 3 more `DevUtilsServiceApplicationTests` cases from the ninth, and 9 new HTML
entity unit tests plus 2 more `DevUtilsServiceApplicationTests` cases from the tenth — see each
follow-up's own note for the full breakdown), verified via a real
`mvn -pl dev-utils-service -am test` run (JDK 21).

## Rules specific to this module

- **Don't force a new operation's request/response shape (or its `execute(...)` signature) to
  match an existing one just for consistency.** `DevUtilOperation` is a bare marker interface and
  `dto/` DTOs are shared only where the shape genuinely matches (see both their own Javadoc) —
  this was a real, corrected mistake: an earlier revision forced every operation through one
  shared `execute(String input, boolean minify): String` signature and one shared request/response
  DTO pair, which only looked reasonable because every operation at the time really was "text in, a
  minify flag, text out." A future operation with a genuinely different shape (Unix Time Converter:
  timestamp+timezone+format; Number Base Converter: value+two integer bases) gets its own request/
  response type and its own `execute(...)` signature, not a bent version of an existing one.
- **Depends only on `common` + `infra`.** Never add a Maven dependency on `gateway`,
  `ecommerce-service`, `identity-service`, `task-service`, `social-service`, `content-service`, or
  `ai-service` — and none of them may depend on this module either. A future operation that needs
  data from another service would need a real HTTP call, never a Maven dependency.
- **Don't add persistence to this module without confirming scope first.** If a future feature
  wants saved/shareable snippets or per-user history, that's a real fork in this module's
  architecture (its own Postgres schema, Liquibase changelog, and — since a "per-user" concept
  needs a caller — likely JWT verification too), not a small addition; it changes the "nothing to
  persist, no caller to authenticate" framing this whole module is built around.
- **Don't add JWT/JWT-derived authorization to an existing endpoint without confirming scope
  first**, for the same reason in reverse — every endpoint here being genuinely public is a
  deliberate, documented choice (see "What lives here" above and `gateway/CLAUDE.md`'s matching
  carve-out), not an oversight to "fix."
- **New endpoints need a matching `permitAll()` in *this* module's own `SecurityConfig` and a
  matching route (plus its own `permitAll()` carve-out) in `gateway`'s** — this module's filter
  chain currently permits everything unconditionally (`anyRequest().permitAll()`), so a new
  endpoint under `/api/v1/dev-utils/**` needs no local change, but `gateway`'s own routing still
  needs the same `route(path(...), http(baseUrl))` line every other service's new endpoint needs
  (see `gateway/CLAUDE.md`'s standing warning about this exact class of gap) — a new top-level
  prefix, unlike a new sub-path under `/api/v1/dev-utils/**`, would need its own `permitAll()` line
  in `gateway`'s `SecurityConfig` too.
- **No Dockerfile/compose dependency on `services-liquibase`, Postgres, Redis, MinIO, or
  Keycloak** — this module's own `docker-compose.apps.yml` block has no `depends_on` at all. Don't
  add one without a concrete new reason (e.g. a future feature that genuinely calls another
  service).
