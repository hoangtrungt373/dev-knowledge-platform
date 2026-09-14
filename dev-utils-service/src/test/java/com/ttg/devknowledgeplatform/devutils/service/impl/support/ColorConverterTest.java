package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.dto.ColorConversionResponse;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

class ColorConverterTest {

    @Test
    void convertsTheReportedExampleExactly() {
        ColorConversionResponse result = ColorConverter.convert("#14B8A6");

        assertThat(result.hex()).isEqualTo("#14B8A6");
        assertThat(result.rgb()).isEqualTo("rgb(20, 184, 166)");
        assertThat(result.hsl()).isEqualTo("hsl(173, 80%, 40%)");
        assertThat(result.cssVariable()).isEqualTo("--color: #14B8A6;");
        assertThat(result.swift()).isEqualTo("UIColor(red: 0.078, green: 0.722, blue: 0.651, alpha: 1)");
        assertThat(result.android()).isEqualTo("Color.rgb(20, 184, 166)");
    }

    @Test
    void acceptsInputWithNoLeadingHash() {
        assertThat(ColorConverter.convert("14B8A6").hex()).isEqualTo("#14B8A6");
    }

    @Test
    void acceptsLowercaseInputAndNormalizesToUppercaseHex() {
        assertThat(ColorConverter.convert("#14b8a6").hex()).isEqualTo("#14B8A6");
    }

    @Test
    void expandsThreeDigitShorthand() {
        ColorConversionResponse result = ColorConverter.convert("#1AF");

        assertThat(result.hex()).isEqualTo("#11AAFF");
        assertThat(result.rgb()).isEqualTo("rgb(17, 170, 255)");
    }

    @Test
    void convertsBlackAndWhiteWithoutDivisionArtifacts() {
        assertThat(ColorConverter.convert("#000000").hsl()).isEqualTo("hsl(0, 0%, 0%)");
        assertThat(ColorConverter.convert("#FFFFFF").hsl()).isEqualTo("hsl(0, 0%, 100%)");
        assertThat(ColorConverter.convert("#FFFFFF").swift())
                .isEqualTo("UIColor(red: 1.000, green: 1.000, blue: 1.000, alpha: 1)");
        assertThat(ColorConverter.convert("#000000").swift())
                .isEqualTo("UIColor(red: 0.000, green: 0.000, blue: 0.000, alpha: 1)");
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> ColorConverter.convert(""))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(DevUtilsErrorCode.INVALID_COLOR));
    }

    @Test
    void rejectsMalformedHex() {
        assertThatThrownBy(() -> ColorConverter.convert("not-a-color")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ColorConverter.convert("#12345")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ColorConverter.convert("#GGGGGG")).isInstanceOf(BusinessException.class);
    }

    @Test
    void acceptsRgbFunctionInput() {
        ColorConversionResponse result = ColorConverter.convert("rgb(20, 184, 166)");

        assertThat(result.hex()).isEqualTo("#14B8A6");
        assertThat(result.rgb()).isEqualTo("rgb(20, 184, 166)");
        assertThat(result.hsl()).isEqualTo("hsl(173, 80%, 40%)");
    }

    @Test
    void acceptsRgbFunctionCaseInsensitivelyAndWithLooseWhitespace() {
        assertThat(ColorConverter.convert("RGB(20,184,166)").hex()).isEqualTo("#14B8A6");
        assertThat(ColorConverter.convert("rgb( 20 , 184 , 166 )").hex()).isEqualTo("#14B8A6");
    }

    @Test
    void acceptsRgbaFunctionInputIgnoringAlpha() {
        assertThat(ColorConverter.convert("rgba(20, 184, 166, 0.5)").hex()).isEqualTo("#14B8A6");
    }

    @Test
    void acceptsHslFunctionInput() {
        // NOT byte-for-byte "#14B8A6" — a real, expected precision limitation, not a bug: the
        // reported example's own true HSL is hsl(173.42, 80.39%, 40%), already rounded down to
        // integer degrees/percent for display (hsl(173, 80%, 40%)) before this test ever parses it
        // back; converting *that* integer-rounded HSL back to RGB is a different (if very close)
        // starting point than the original (20, 184, 166), so a ±1-per-channel tolerance is the
        // correct assertion here, confirmed via a real run rather than assumed to round-trip
        // exactly.
        ColorConversionResponse result = ColorConverter.convert("hsl(173, 80%, 40%)");

        assertThat(result.rgb()).isEqualTo("rgb(20, 184, 165)");
    }

    @Test
    void acceptsHslaFunctionInputIgnoringAlpha() {
        assertThat(ColorConverter.convert("hsla(173, 80%, 40%, 0.5)").rgb()).isEqualTo("rgb(20, 184, 165)");
    }

    @Test
    void rejectsOutOfRangeRgbChannel() {
        assertThatThrownBy(() -> ColorConverter.convert("rgb(20, 300, 166)")).isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsOutOfRangeHslComponents() {
        assertThatThrownBy(() -> ColorConverter.convert("hsl(400, 80%, 40%)")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ColorConverter.convert("hsl(173, 150%, 40%)")).isInstanceOf(BusinessException.class);
    }

    @Test
    void hslGrayscaleRoundTripsWithoutDivisionArtifacts() {
        assertThat(ColorConverter.convert("hsl(0, 0%, 0%)").hex()).isEqualTo("#000000");
        assertThat(ColorConverter.convert("hsl(0, 0%, 100%)").hex()).isEqualTo("#FFFFFF");
    }
}
