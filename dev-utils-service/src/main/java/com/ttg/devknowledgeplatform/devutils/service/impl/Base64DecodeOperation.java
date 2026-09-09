package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Decodes a standard Base64 string ({@link Base64#getDecoder()} — the {@code +}/{@code /}
 * alphabet, not URL-safe {@code -}/{@code _}, matching {@link Base64EncodeOperation}'s own output)
 * back into text, read as UTF-8 ({@link StandardCharsets#UTF_8}). {@code input} is
 * {@link String#strip()}ped first — a leading/trailing newline is a common artifact of pasting a
 * Base64 string from elsewhere and shouldn't fail an otherwise-valid decode — but the decoder
 * itself stays strict beyond that: any embedded whitespace or other character outside the Base64
 * alphabet is rejected rather than silently skipped. Deliberately not
 * {@link Base64#getMimeDecoder()}, which ignores non-alphabet characters anywhere in the input —
 * that would risk quietly "fixing" genuinely malformed input instead of reporting it, the same
 * "never guess, report real errors" posture this module's other real-parser-backed operations
 * (e.g. {@code XmlOperation}, {@code PhpToJsonOperation}) already follow.
 *
 * <p>Real failure path, the same "real parse, real invalid-input error" shape those two operations
 * already establish — {@code DevUtilsErrorCode.INVALID_BASE64}, backed by
 * {@link IllegalArgumentException}'s own message ({@code java.util.Base64}'s own decoder messages,
 * e.g. "Illegal base64 character 20", are terse but already reasonably specific about what's
 * wrong — not reworded further).
 *
 * <p>Decoded bytes that aren't themselves valid UTF-8 are <b>not</b> a separate failure case — a
 * successfully Base64-decoded byte sequence might not actually be UTF-8 text at all (e.g.
 * arbitrary binary data, or text in another encoding), and {@code new String(bytes, UTF_8)}'s own
 * standard Java behavior is to replace any invalid byte sequence with the U+FFFD replacement
 * character rather than throw — this operation intentionally doesn't add its own stricter
 * validation on top of that; a garbled result for that case is expected, not a bug.
 */
@Component
public class Base64DecodeOperation implements DevUtilOperation {

    @Override
    public OperationGroup group() {
        return OperationGroup.ENCODERS_DECODERS;
    }

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_BASE64} when
     *                           {@code input} isn't valid Base64
     */
    public String execute(String input) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(input.strip());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_BASE64, (Object) e.getMessage());
        }
        return new String(decoded, StandardCharsets.UTF_8);
    }
}
