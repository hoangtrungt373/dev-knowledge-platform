package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.dto.HashResponse;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Computes every {@link HashResponse} digest at once — SHA-1, SHA-256, SHA-384, SHA-512 — over
 * the raw UTF-8 bytes of the input. The first operation to declare
 * {@link OperationGroup#INSPECTORS} — the concrete "hash calculator" example that group's own
 * Javadoc named ahead of use. Same "one action, a response genuinely richer than one string"
 * shape {@code StringCaseOperation}/{@code StringCaseResponse} already establish — a caller
 * pasting one input almost always wants to compare it against more than one algorithm at once,
 * not commit to a single one up front the way {@code Base64EncodeOperation}/{@code
 * UrlEncodeOperation} each commit to one transform.
 *
 * <p>Hex-encoded lowercase via {@link HexFormat#of()} — the JDK's own fixed-width, lowercase hex
 * formatter (Java 17+), not a hand-rolled {@code String.format("%02x", b)} loop.
 *
 * <p>Never throws — {@code SHA-1}/{@code SHA-256}/{@code SHA-384}/{@code SHA-512} are all
 * guaranteed present in every standard JDK security provider, so this class's own private
 * {@code digest} helper catches {@link NoSuchAlgorithmException} and rethrows it as an
 * {@link AssertionError} (a truly unreachable path, not a real failure) rather than surfacing a
 * checked exception on {@link #execute(String)} — no matching {@code DevUtilsErrorCode} exists
 * for this operation.
 */
@Component
public class HashGeneratorOperation implements DevUtilOperation {

    @Override
    public OperationGroup group() {
        return OperationGroup.INSPECTORS;
    }

    /**
     * Never throws — see this class's own Javadoc for why every one of the four algorithms is
     * always available.
     */
    public HashResponse execute(String input) {
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        return new HashResponse(
                digest("SHA-1", bytes),
                digest("SHA-256", bytes),
                digest("SHA-384", bytes),
                digest("SHA-512", bytes));
    }

    private static String digest(String algorithm, byte[] input) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(messageDigest.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(algorithm + " is guaranteed to be available on every JDK", e);
        }
    }
}
