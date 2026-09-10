package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.exception.ParsingExceptionMessages;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.JsonNodeIo;

import lombok.RequiredArgsConstructor;

/**
 * Reads a JWT's header and payload without verifying its signature — a debugging aid (mirroring
 * jwt.io's own "Decoded" panel), not an authentication check: this module holds no key material to
 * verify against in the first place, and a fully public, unauthenticated endpoint has no business
 * asserting a token is trustworthy anyway. The first operation to actually declare
 * {@link OperationGroup#INSPECTORS} — see that enum's own Javadoc for the worked example this
 * fulfills.
 *
 * <p>Splits {@code input} on {@code '.'} into exactly 3 segments — header, payload, signature, the
 * JWS Compact Serialization shape every JWT uses (RFC 7515 §3.1) — Base64URL-decodes the first two
 * ({@link Base64#getUrlDecoder()}, tolerating the unpadded form every real-world JWT actually uses)
 * as UTF-8 JSON text, and re-embeds each as a real nested object in the result rather than as an
 * escaped string. The third segment (the signature) is carried through <b>verbatim, never
 * decoded</b> — a real signature is arbitrary binary, essentially never valid UTF-8 text, so its raw
 * Base64URL form is the only representation that means anything to a reader; this operation makes
 * no attempt to verify it (see above), so there's nothing further to compute from it either.
 */
@Component
@RequiredArgsConstructor
public class JwtDebuggerOperation implements DevUtilOperation {

    private final ObjectMapper objectMapper;

    @Override
    public OperationGroup group() {
        return OperationGroup.INSPECTORS;
    }

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_JWT} when {@code input}
     *                           isn't exactly 3 dot-separated segments, or either the header or
     *                           payload segment isn't valid Base64URL-encoded JSON
     */
    public String execute(String input, boolean minify) {
        String[] segments = input.strip().split("\\.", -1);
        if (segments.length != 3) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_JWT, (Object) (
                    "expected 3 dot-separated segments (header.payload.signature), found " + segments.length));
        }

        JsonNode header = decodeJsonSegment(segments[0], "header");
        JsonNode payload = decodeJsonSegment(segments[1], "payload");

        ObjectNode result = objectMapper.createObjectNode();
        result.set("header", header);
        result.set("payload", payload);
        result.put("signature", segments[2]);

        return JsonNodeIo.write(objectMapper, result, minify, DevUtilsErrorCode.INVALID_JWT);
    }

    /**
     * Base64URL-decodes {@code segment}, then parses the result as JSON — deliberately not
     * {@link JsonNodeIo#readTree}, since that helper has no way to name *which* segment failed;
     * this method's own two catch clauses prepend {@code segmentName} to each failure so
     * "header segment is not valid Base64URL"/"payload segment is not valid JSON" reads as a
     * genuinely more useful error than either failure would on its own.
     */
    private JsonNode decodeJsonSegment(String segment, String segmentName) {
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(segment);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_JWT, (Object) (
                    segmentName + " segment is not valid Base64URL: " + e.getMessage()));
        }
        String json = new String(decoded, StandardCharsets.UTF_8);
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_JWT, (Object) (
                    segmentName + " segment is not valid JSON: " + ParsingExceptionMessages.friendlyMessage(e)));
        }
    }
}
