package com.ttg.devknowledgeplatform.devutils.service.impl;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.dto.ColorConversionResponse;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;
import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.ColorConverter;

/**
 * Converts a hex, {@code rgb(...)}, or {@code hsl(...)} color into
 * {@link ColorConversionResponse}'s own six representations at once (HEX/RGB/HSL/CSS variable/
 * Swift/Android) — a thin pass-through to {@link ColorConverter}, the same "operation class stays
 * thin, the real logic lives in a shared {@code service.impl.support} utility" shape
 * {@code CssOperation}/{@code HtmlToTsxOperation} already establish. The fourth
 * {@link OperationGroup#WEB} operation.
 *
 * <p>See {@link ColorConverter}'s own Javadoc for the full parsing/conversion rules — the 3
 * accepted input shapes (originally hex-only, matching the GUI's own native color picker;
 * broadened to also accept {@code rgb(...)}/{@code hsl(...)} per direct follow-up request), a
 * fixed generic {@code --color} CSS variable name (per direct request), and why this is the one
 * {@code OperationGroup#WEB} operation with a real invalid-input failure path (unlike
 * {@code HtmlPreviewOperation}/{@code MarkdownPreviewOperation}, both lenient; {@code
 * HtmlToTsxOperation}, also lenient).
 */
@Component
public class ColorConverterOperation implements DevUtilOperation {

    @Override
    public OperationGroup group() {
        return OperationGroup.WEB;
    }

    /**
     * Converts {@code input} into every {@link ColorConversionResponse} representation at once —
     * see {@link ColorConverter}'s own Javadoc for the full rules.
     *
     * @param input a hex color ({@code "#14B8A6"}, with or without {@code #}, 3- or 6-digit), an
     *              {@code rgb(20, 184, 166)} function, or an {@code hsl(173, 80%, 40%)} function
     * @return every representation of {@code input} at once
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_COLOR} when
     *                           {@code input} doesn't match any of the 3 accepted shapes
     */
    public ColorConversionResponse execute(String input) {
        return ColorConverter.convert(input);
    }
}
