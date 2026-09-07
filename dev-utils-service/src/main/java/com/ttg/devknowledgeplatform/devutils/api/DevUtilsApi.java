package com.ttg.devknowledgeplatform.devutils.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ttg.devknowledgeplatform.devutils.dto.DevUtilResponse;
import com.ttg.devknowledgeplatform.devutils.dto.MinifiableTextRequest;
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
 * <p>Each endpoint takes whichever request DTO actually fits its own operation — three of the four
 * genuinely share {@link MinifiableTextRequest}'s shape, {@code jsonToYaml} does not (see
 * {@link TextRequest}'s own Javadoc) — rather than every endpoint being forced through one shared
 * request type; see {@code service.DevUtilOperation}'s own Javadoc for the full reasoning.
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
}
