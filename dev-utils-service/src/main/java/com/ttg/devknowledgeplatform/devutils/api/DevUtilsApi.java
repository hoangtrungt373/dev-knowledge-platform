package com.ttg.devknowledgeplatform.devutils.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ttg.devknowledgeplatform.devutils.dto.DevUtilRequest;
import com.ttg.devknowledgeplatform.devutils.dto.DevUtilResponse;

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
 */
@RequestMapping("/api/v1/dev-utils")
public interface DevUtilsApi {

    /**
     * Validates a raw JSON string and returns it pretty-printed.
     *
     * @return {@code 200} with the formatted JSON, or {@code 400} if {@code request.input()} is
     *         not valid JSON
     */
    @PostMapping("/json/format")
    ResponseEntity<DevUtilResponse> formatJson(@Valid @RequestBody DevUtilRequest request);

    /**
     * Converts a raw YAML string to pretty-printed JSON.
     *
     * @return {@code 200} with the converted JSON, or {@code 400} if {@code request.input()} is
     *         not valid YAML
     */
    @PostMapping("/yaml-to-json")
    ResponseEntity<DevUtilResponse> yamlToJson(@Valid @RequestBody DevUtilRequest request);

    /**
     * Converts a raw JSON string to YAML.
     *
     * @return {@code 200} with the converted YAML, or {@code 400} if {@code request.input()} is
     *         not valid JSON
     */
    @PostMapping("/json-to-yaml")
    ResponseEntity<DevUtilResponse> jsonToYaml(@Valid @RequestBody DevUtilRequest request);

    /**
     * Reformats raw HTML with consistent indentation.
     *
     * @return {@code 200} with the beautified HTML
     */
    @PostMapping("/html/beautify")
    ResponseEntity<DevUtilResponse> beautifyHtml(@Valid @RequestBody DevUtilRequest request);
}
