package com.ttg.devknowledgeplatform.devutils.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ttg.devknowledgeplatform.devutils.dto.DevUtilResponse;
import com.ttg.devknowledgeplatform.devutils.dto.MinifiableTextRequest;
import com.ttg.devknowledgeplatform.devutils.dto.StringCaseResponse;
import com.ttg.devknowledgeplatform.devutils.dto.TextRequest;

import jakarta.validation.Valid;

/**
 * HTTP contract for the stateless developer-utility operations. The implementation
 * ({@link com.ttg.devknowledgeplatform.devutils.api.impl.DevUtilsController}) carries no HTTP
 * annotations, same split as every other module's {@code api}/{@code api.impl} pair.
 *
 * <p>Every endpoint here is public — no {@code @CurrentUserId}, no authenticated principal at all.
 * See {@code security.SecurityConfig}'s Javadoc and root {@code CLAUDE.md}'s Security section for
 * why this is the one deployable in the reactor that isn't a JWT resource server, and this
 * module's own {@code CLAUDE.md} for the matching carve-out {@code gateway}'s own routing needs.
 *
 * <p>Each endpoint takes whichever request DTO actually fits its own operation — most genuinely
 * share {@link MinifiableTextRequest}'s shape (raw text in, a minify flag, transformed text out);
 * {@code jsonToYaml}/{@code jsonToCsv} do not, since neither YAML nor CSV has a distinct "compact"
 * form to toggle (see {@link TextRequest}'s own Javadoc) — rather than every endpoint being forced
 * through one shared request type; see {@code service.DevUtilOperation}'s own Javadoc for the full
 * reasoning.
 */
@RequestMapping("/api/v1/dev-utils")
public interface DevUtilsApi {

    /**
     * Validates a raw JSON string and returns it pretty-printed, or, with
     * {@code request.minify()}, compact/single-line.
     *
     * @return {@code 200} with the formatted JSON, or {@code 400} if {@code request.input()} is
     *         not valid JSON
     */
    @PostMapping("/json/format")
    ResponseEntity<DevUtilResponse> formatJson(@Valid @RequestBody MinifiableTextRequest request);

    /**
     * Converts a raw YAML string to JSON, pretty-printed or (with {@code request.minify()})
     * compact/single-line.
     *
     * @return {@code 200} with the converted JSON, or {@code 400} if {@code request.input()} is
     *         not valid YAML
     */
    @PostMapping("/yaml-to-json")
    ResponseEntity<DevUtilResponse> yamlToJson(@Valid @RequestBody MinifiableTextRequest request);

    /**
     * Converts a raw JSON string to YAML. No minify option — see {@link TextRequest}'s own
     * Javadoc for why.
     *
     * @return {@code 200} with the converted YAML, or {@code 400} if {@code request.input()} is
     *         not valid JSON
     */
    @PostMapping("/json-to-yaml")
    ResponseEntity<DevUtilResponse> jsonToYaml(@Valid @RequestBody TextRequest request);

    /**
     * Reformats raw HTML with consistent indentation, or, with {@code request.minify()}, jsoup's
     * own unformatted output mode.
     *
     * @return {@code 200} with the beautified (or minified) HTML
     */
    @PostMapping("/html/beautify")
    ResponseEntity<DevUtilResponse> beautifyHtml(@Valid @RequestBody MinifiableTextRequest request);

    /**
     * Reformats raw CSS with consistent indentation, or, with {@code request.minify()}, a compact
     * form with comments and non-essential whitespace stripped. Lenient — see
     * {@code CssOperation}'s own Javadoc — this never fails on malformed input.
     *
     * @return {@code 200} with the beautified (or minified) CSS
     */
    @PostMapping("/css/beautify")
    ResponseEntity<DevUtilResponse> beautifyCss(@Valid @RequestBody MinifiableTextRequest request);

    /**
     * Reformats raw LESS with consistent indentation, or, with {@code request.minify()}, a compact
     * form with comments and non-essential whitespace stripped. Reformats only — does not compile
     * LESS to plain CSS (see {@code LessOperation}'s own Javadoc) — and, like {@code beautifyCss},
     * never fails on malformed input.
     *
     * @return {@code 200} with the beautified (or minified) LESS
     */
    @PostMapping("/less/beautify")
    ResponseEntity<DevUtilResponse> beautifyLess(@Valid @RequestBody MinifiableTextRequest request);

    /**
     * Reformats raw SCSS with consistent indentation, or, with {@code request.minify()}, a compact
     * form with comments and non-essential whitespace stripped. Reformats only — does not compile
     * SCSS to plain CSS (see {@code ScssOperation}'s own Javadoc) — and, like {@code beautifyCss},
     * never fails on malformed input.
     *
     * @return {@code 200} with the beautified (or minified) SCSS
     */
    @PostMapping("/scss/beautify")
    ResponseEntity<DevUtilResponse> beautifyScss(@Valid @RequestBody MinifiableTextRequest request);

    /**
     * Reformats raw JavaScript with consistent indentation, or, with {@code request.minify()}, a
     * compact form with comments and non-essential whitespace stripped. See {@code JsOperation}'s
     * own Javadoc for its Automatic Semicolon Insertion (ASI) safety guarantee — this never fails
     * on malformed input.
     *
     * @return {@code 200} with the beautified (or minified) JavaScript
     */
    @PostMapping("/js/beautify")
    ResponseEntity<DevUtilResponse> beautifyJs(@Valid @RequestBody MinifiableTextRequest request);

    /**
     * Reformats ERB (Embedded RuBy) markup with consistent indentation, or, with
     * {@code request.minify()}, jsoup's own unformatted output mode — {@code <% %>}/{@code <%= %>}
     * tags are preserved verbatim regardless (see {@code ErbOperation}'s own Javadoc). Never fails
     * on malformed input, the same jsoup-backed leniency {@code beautifyHtml} has.
     *
     * @return {@code 200} with the beautified (or minified) ERB
     */
    @PostMapping("/erb/beautify")
    ResponseEntity<DevUtilResponse> beautifyErb(@Valid @RequestBody MinifiableTextRequest request);

    /**
     * Validates a raw XML string and re-serializes it indented, or, with {@code request.minify()},
     * with insignificant inter-element whitespace stripped.
     *
     * @return {@code 200} with the beautified (or minified) XML, or {@code 400} if
     *         {@code request.input()} is not well-formed XML
     */
    @PostMapping("/xml/beautify")
    ResponseEntity<DevUtilResponse> beautifyXml(@Valid @RequestBody MinifiableTextRequest request);

    /**
     * Converts a raw JSON array of objects (or a single object) to CSV. No minify option — see
     * {@link TextRequest}'s own Javadoc for why; CSV has no distinct "compact" form to toggle.
     *
     * @return {@code 200} with the converted CSV, or {@code 400} if {@code request.input()} isn't
     *         valid JSON shaped as an array of objects (or a single object)
     */
    @PostMapping("/json-to-csv")
    ResponseEntity<DevUtilResponse> jsonToCsv(@Valid @RequestBody TextRequest request);

    /**
     * Converts raw CSV (first row treated as the header) to a JSON array of objects, pretty-printed
     * or (with {@code request.minify()}) compact/single-line.
     *
     * @return {@code 200} with the converted JSON, or {@code 400} if {@code request.input()} is
     *         not valid CSV
     */
    @PostMapping("/csv-to-json")
    ResponseEntity<DevUtilResponse> csvToJson(@Valid @RequestBody MinifiableTextRequest request);

    /**
     * Reformats a raw SQL query/script with one clause per line, or, with
     * {@code request.minify()}, a compact form with comments stripped. Lenient — see
     * {@code SqlFormatOperation}'s own Javadoc — this never fails on malformed input.
     *
     * @return {@code 200} with the formatted (or minified) SQL
     */
    @PostMapping("/sql/format")
    ResponseEntity<DevUtilResponse> formatSql(@Valid @RequestBody MinifiableTextRequest request);

    /**
     * Converts a raw PHP array literal ({@code [...]} or legacy {@code array(...)}) to JSON,
     * pretty-printed or (with {@code request.minify()}) compact/single-line.
     *
     * @return {@code 200} with the converted JSON, or {@code 400} if {@code request.input()} is
     *         not a valid PHP array literal
     */
    @PostMapping("/php-to-json")
    ResponseEntity<DevUtilResponse> phpToJson(@Valid @RequestBody MinifiableTextRequest request);

    /**
     * Converts a raw JSON value to a PHP array literal, one clause per line, or (with
     * {@code request.minify()}) compact/single-line.
     *
     * @return {@code 200} with the converted PHP, or {@code 400} if {@code request.input()} is
     *         not valid JSON
     */
    @PostMapping("/json-to-php")
    ResponseEntity<DevUtilResponse> jsonToPhp(@Valid @RequestBody MinifiableTextRequest request);

    /**
     * Converts raw text into every {@link StringCaseResponse} case variant at once (camelCase,
     * PascalCase, snake_case, kebab-case, CONSTANT_CASE, Title Case, Sentence case). No minify
     * option — there's no "compact form" of a case conversion — and this never fails on any input.
     *
     * @return {@code 200} with every case variant
     */
    @PostMapping("/string-case/convert")
    ResponseEntity<StringCaseResponse> convertStringCase(@Valid @RequestBody TextRequest request);

    /**
     * Encodes raw text as a standard Base64 string. No minify option — see {@link TextRequest}'s
     * own Javadoc for why; a Base64 encoding has no distinct "compact form" to toggle. Never fails
     * — every string has a valid encoding.
     *
     * @return {@code 200} with the Base64-encoded text
     */
    @PostMapping("/base64/encode")
    ResponseEntity<DevUtilResponse> encodeBase64(@Valid @RequestBody TextRequest request);

    /**
     * Decodes a standard Base64 string back into text.
     *
     * @return {@code 200} with the decoded text, or {@code 400} if {@code request.input()} isn't
     *         valid Base64
     */
    @PostMapping("/base64/decode")
    ResponseEntity<DevUtilResponse> decodeBase64(@Valid @RequestBody TextRequest request);

    /**
     * Percent-encodes raw text for safe use inside a URL. No minify option — see
     * {@link TextRequest}'s own Javadoc for why; a percent-encoding has no distinct "compact form"
     * to toggle. Never fails — every string has a valid encoding.
     *
     * @return {@code 200} with the URL-encoded text
     */
    @PostMapping("/url/encode")
    ResponseEntity<DevUtilResponse> encodeUrl(@Valid @RequestBody TextRequest request);

    /**
     * Decodes a percent-encoded URL string back into text.
     *
     * @return {@code 200} with the decoded text, or {@code 400} if {@code request.input()} isn't
     *         validly percent-encoded
     */
    @PostMapping("/url/decode")
    ResponseEntity<DevUtilResponse> decodeUrl(@Valid @RequestBody TextRequest request);

    /**
     * Escapes {@code & < > " '} into their named HTML character references. No minify option —
     * see {@link TextRequest}'s own Javadoc for why; an escaped form has no distinct "compact
     * form" to toggle. Never fails — every string has a valid escaped form.
     *
     * @return {@code 200} with the HTML-entity-encoded text
     */
    @PostMapping("/html-entity/encode")
    ResponseEntity<DevUtilResponse> encodeHtmlEntity(@Valid @RequestBody TextRequest request);

    /**
     * Decodes HTML character references back into their literal characters. Never fails — an
     * unrecognized {@code &...;} sequence is left untouched rather than rejected.
     *
     * @return {@code 200} with the decoded text
     */
    @PostMapping("/html-entity/decode")
    ResponseEntity<DevUtilResponse> decodeHtmlEntity(@Valid @RequestBody TextRequest request);
}
