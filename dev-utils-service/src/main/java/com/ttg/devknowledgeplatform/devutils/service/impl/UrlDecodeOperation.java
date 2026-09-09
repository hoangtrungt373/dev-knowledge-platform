package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Decodes a percent-encoded URL string back into text, UTF-8 ({@link StandardCharsets#UTF_8}).
 * Delegates to {@link URLDecoder#decode(String, java.nio.charset.Charset)}, which — matching
 * {@link UrlEncodeOperation}'s own output, and conventional query-string decoding generally — also
 * decodes a literal {@code +} back into a space.
 *
 * <p>Real failure path, the same "real parse, real invalid-input error" shape
 * {@link Base64DecodeOperation} already establishes — {@code DevUtilsErrorCode.INVALID_URL_ENCODING},
 * backed by {@link IllegalArgumentException}'s own message ({@code URLDecoder}'s own decoder
 * messages, e.g. {@code "URLDecoder: Incomplete trailing escape (%) pattern"}, are already
 * reasonably specific about what's wrong — not reworded further).
 */
@Component
public class UrlDecodeOperation implements DevUtilOperation {

    @Override
    public OperationGroup group() {
        return OperationGroup.ENCODERS_DECODERS;
    }

    /**
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_URL_ENCODING} when
     *                           {@code input} isn't validly percent-encoded
     */
    public String execute(String input) {
        try {
            return URLDecoder.decode(input, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_URL_ENCODING, (Object) e.getMessage());
        }
    }
}
