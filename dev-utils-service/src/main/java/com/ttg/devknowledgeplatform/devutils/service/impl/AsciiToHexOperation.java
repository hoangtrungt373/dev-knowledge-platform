package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Converts raw text into its own UTF-8 bytes' hex representation — one lowercase two-digit hex
 * pair per byte, space-separated (e.g. {@code "Hi"} → {@code "48 69"}) — via
 * {@link HexFormat#ofDelimiter(String)} (Java 17+), not a hand-rolled
 * {@code String.format("%02x", b)} loop.
 *
 * <p>Operates on the input's UTF-8 bytes, not raw Java {@code char}s — the same "always encode
 * UTF-8 bytes explicitly, never the platform-default charset" discipline
 * {@link Base64EncodeOperation}/{@link UrlEncodeOperation} already establish, so a non-ASCII
 * character (despite this operation's own "ASCII" name — a plain byte-to-hex converter has no
 * real reason to reject one) still produces a well-defined result that round-trips correctly
 * through {@link HexToAsciiOperation} — verified against a real standalone Java harness for a
 * genuinely multi-byte input first, not assumed.
 *
 * <p>Never throws — every string has a valid UTF-8 byte representation, so no matching
 * {@code DevUtilsErrorCode} exists for this operation.
 */
@Component
public class AsciiToHexOperation implements DevUtilOperation {

    @Override
    public OperationGroup group() {
        return OperationGroup.ENCODERS_DECODERS;
    }

    /** Never throws — see this class's own Javadoc for why every string has a valid hex form. */
    public String execute(String input) {
        return HexFormat.ofDelimiter(" ").formatHex(input.getBytes(StandardCharsets.UTF_8));
    }
}
