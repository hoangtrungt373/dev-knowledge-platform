package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Percent-encodes raw text for safe use inside a URL (a query string value or path segment), UTF-8
 * ({@link StandardCharsets#UTF_8}). Delegates to
 * {@link URLEncoder#encode(String, java.nio.charset.Charset)} — the JDK's own
 * {@code application/x-www-form-urlencoded} encoder — then corrects its one divergence from
 * conventional URL percent-encoding: that encoder represents a space as {@code +}, not
 * {@code %20}, since it targets HTML form submission rather than a generic URI component (the same
 * distinction JavaScript's {@code encodeURIComponent} sidesteps by never emitting {@code +} at
 * all). Every other reserved character (e.g. {@code : / ? # & =}) is left exactly as
 * {@code URLEncoder} already produces it.
 *
 * <p>Never throws — every string, however unusual, has a valid percent-encoding, so no matching
 * {@code DevUtilsErrorCode} exists for this operation (the same reasoning
 * {@link Base64EncodeOperation}'s own Javadoc gives for itself).
 */
@Component
public class UrlEncodeOperation implements DevUtilOperation {

    @Override
    public OperationGroup group() {
        return OperationGroup.ENCODERS_DECODERS;
    }

    /** Never throws — see this class's own Javadoc for why every string has a valid encoding. */
    public String execute(String input) {
        return URLEncoder.encode(input, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
