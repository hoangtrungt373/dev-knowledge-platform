package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Decodes a hex string back into text — {@link AsciiToHexOperation}'s own space-separated
 * {@code "53 65 ..."} form, or a continuous run of hex digits with no separator at all
 * ({@code "5365..."}), or any other whitespace-separated shape (tabs/newlines). Whitespace is
 * stripped first via a plain regex, then the remainder is parsed as one continuous hex string via
 * {@link HexFormat#parseHex(CharSequence)} — tolerant of either input shape, rather than
 * committing to one exact delimiter grammar.
 *
 * <p>Real failure path, the same "real parse, real invalid-input error" shape
 * {@link Base64DecodeOperation} already establishes — {@code DevUtilsErrorCode.INVALID_HEX},
 * backed by {@link HexFormat#parseHex}'s own {@link IllegalArgumentException} message (an odd
 * number of hex digits, or a non-hex character — {@link NumberFormatException} for the latter is
 * itself an {@code IllegalArgumentException} subtype, so one catch clause covers both) — confirmed
 * against a real standalone Java harness first: {@code "string length not even: 3"},
 * {@code "not a hexadecimal digit: \"z\" = 122"}, both already specific enough not to need
 * rewording.
 *
 * <p>Decoded bytes that aren't themselves valid UTF-8 are <b>not</b> a separate failure case — the
 * same reasoning {@link Base64DecodeOperation}'s own Javadoc gives for itself: a successfully
 * hex-decoded byte sequence might not actually be UTF-8 text (arbitrary binary data), and
 * {@code new String(bytes, UTF_8)}'s own standard replacement-character behavior applies as-is,
 * with no stricter validation added on top.
 */
@Component
public class HexToAsciiOperation implements DevUtilOperation {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public OperationGroup group() {
        return OperationGroup.ENCODERS_DECODERS;
    }

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_HEX} when
     *                           {@code input} isn't a valid hex string
     */
    public String execute(String input) {
        String stripped = WHITESPACE.matcher(input).replaceAll("");
        byte[] bytes;
        try {
            bytes = HexFormat.of().parseHex(stripped);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_HEX, (Object) e.getMessage());
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
