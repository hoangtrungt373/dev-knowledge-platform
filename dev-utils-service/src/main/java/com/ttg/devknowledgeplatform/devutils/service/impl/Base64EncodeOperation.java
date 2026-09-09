package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Encodes raw text as a standard Base64 string ({@link Base64#getEncoder()} — the {@code +}/
 * {@code /} alphabet, not URL-safe {@code -}/{@code _}, matching what "Base64 string"
 * conventionally means and what {@link Base64DecodeOperation} accepts back). Input is read as
 * UTF-8 ({@link StandardCharsets#UTF_8}), not the JVM's platform-default charset, so encoding is
 * consistent regardless of what machine this service happens to run on — confirmed against a real
 * standalone Java harness with a genuinely multi-byte UTF-8 input (an emoji, U+1F44B) rather than
 * assumed, since the default-platform-charset trap is a real, easy mistake on a non-UTF-8-default
 * platform (e.g. Windows) otherwise.
 *
 * <p>Never throws — every string, however unusual (including multi-byte UTF-8 sequences like
 * emoji or non-Latin scripts), has a valid Base64 encoding, so no matching
 * {@code DevUtilsErrorCode} exists for this operation.
 */
@Component
public class Base64EncodeOperation implements DevUtilOperation {

    @Override
    public OperationGroup group() {
        return OperationGroup.ENCODERS_DECODERS;
    }

    /** Never throws — see this class's own Javadoc for why every string has a valid encoding. */
    public String execute(String input) {
        return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }
}
