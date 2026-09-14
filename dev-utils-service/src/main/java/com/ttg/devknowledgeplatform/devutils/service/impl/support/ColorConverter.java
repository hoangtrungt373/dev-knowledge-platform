package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.dto.ColorConversionResponse;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

/**
 * Parses a color — a {@code #RRGGBB}/{@code #RGB} hex string (with or without the leading
 * {@code #}, either case), an {@code rgb(R, G, B)} function, or an {@code hsl(H, S%, L%)}
 * function (per direct follow-up request — originally hex-only, since the GUI's own native
 * {@code <input type="color">} picker only ever produces hex; broadened once pasting an
 * already-computed {@code rgb(...)}/{@code hsl(...)} value in directly was asked for too) — and
 * renders it as {@link ColorConversionResponse}'s own six representations at once — HEX/RGB/HSL/a
 * CSS custom property/Swift's {@code UIColor}/Android's {@code Color.rgb}. Regardless of which of
 * the 3 input shapes was given, every representation in the response is always freshly computed
 * from the resolved {@code (r, g, b)} triple — the response never simply echoes the input back
 * verbatim, even for the representation matching the input's own shape (an {@code hsl(...)} input
 * still gets its HSL value re-derived from the resolved RGB, not copied from the input string, so
 * out-of-canonical-form input — e.g. non-integer rounding a caller typed by hand — always comes
 * back normalized).
 *
 * <p><b>Real parser, real invalid-input error</b> — the same "genuine failure path" shape
 * {@code XmlOperation}/{@code CsvToJsonOperation}/{@code PhpToJsonOperation} already establish
 * (see {@code DevUtilsErrorCode}'s own Javadoc), unlike the two other lenient Web-group operations
 * ({@code HtmlPreviewOperation}/{@code MarkdownPreviewOperation}).
 */
public final class ColorConverter {

    private static final Pattern HEX_PATTERN = Pattern.compile("^#?([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$");
    private static final Pattern RGB_FUNCTION_PATTERN =
            Pattern.compile("^rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*(?:,\\s*[\\d.]+\\s*)?\\)$",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern HSL_FUNCTION_PATTERN = Pattern.compile(
            "^hsla?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})%\\s*,\\s*(\\d{1,3})%\\s*(?:,\\s*[\\d.]+\\s*)?\\)$",
            Pattern.CASE_INSENSITIVE);

    private ColorConverter() {
    }

    /**
     * @param input a hex color ({@code "#14B8A6"}, {@code "14B8A6"}, or the 3-digit shorthand
     *              {@code "#1AF"}), an {@code rgb(20, 184, 166)}/{@code rgba(20, 184, 166, 1)}
     *              function, or an {@code hsl(173, 80%, 40%)}/{@code hsla(173, 80%, 40%, 1)}
     *              function — any case, whitespace around commas tolerated
     * @return every representation of {@code input} at once
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_COLOR} when
     *                           {@code input} doesn't match any of the 3 accepted shapes, or a
     *                           channel/component is out of its valid range
     */
    public static ColorConversionResponse convert(String input) {
        int[] rgb = parseToRgb(input);
        return buildResponse(rgb[0], rgb[1], rgb[2]);
    }

    private static int[] parseToRgb(String input) {
        String trimmed = input == null ? "" : input.trim();

        if (HEX_PATTERN.matcher(trimmed).matches()) {
            return hexToRgb(trimmed);
        }
        Matcher rgbMatcher = RGB_FUNCTION_PATTERN.matcher(trimmed);
        if (rgbMatcher.matches()) {
            return new int[] {
                    requireChannel(rgbMatcher.group(1), trimmed),
                    requireChannel(rgbMatcher.group(2), trimmed),
                    requireChannel(rgbMatcher.group(3), trimmed)};
        }
        Matcher hslMatcher = HSL_FUNCTION_PATTERN.matcher(trimmed);
        if (hslMatcher.matches()) {
            int hue = requireInRange(hslMatcher.group(1), 0, 360, trimmed);
            int saturation = requireInRange(hslMatcher.group(2), 0, 100, trimmed);
            int lightness = requireInRange(hslMatcher.group(3), 0, 100, trimmed);
            return hslToRgb(hue, saturation, lightness);
        }
        throw new BusinessException(DevUtilsErrorCode.INVALID_COLOR, (Object) (trimmed.isEmpty() ? "blank"
                : trimmed + " (expected #RRGGBB, #RGB, rgb(R, G, B), or hsl(H, S%, L%))"));
    }

    private static int requireChannel(String digits, String originalInput) {
        return requireInRange(digits, 0, 255, originalInput);
    }

    private static int requireInRange(String digits, int min, int max, String originalInput) {
        int value = Integer.parseInt(digits);
        if (value < min || value > max) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_COLOR,
                    (Object) (originalInput + " (" + value + " is outside the valid range " + min + "-" + max + ")"));
        }
        return value;
    }

    private static int[] hexToRgb(String trimmedHex) {
        String digits = trimmedHex.startsWith("#") ? trimmedHex.substring(1) : trimmedHex;
        if (digits.length() == 3) {
            // Shorthand expansion (CSS's own rule): each digit is doubled — "1AF" -> "11AAFF".
            StringBuilder expanded = new StringBuilder(6);
            for (int i = 0; i < 3; i++) {
                char c = digits.charAt(i);
                expanded.append(c).append(c);
            }
            digits = expanded.toString();
        }
        return new int[] {
                Integer.parseInt(digits.substring(0, 2), 16),
                Integer.parseInt(digits.substring(2, 4), 16),
                Integer.parseInt(digits.substring(4, 6), 16)};
    }

    // Standard HSL -> RGB conversion (the CSS Color Module's own algorithm, the inverse of
    // #toHsl below) — each channel rounded to the nearest whole 0-255 value.
    private static int[] hslToRgb(int h, int s, int l) {
        double sf = s / 100.0;
        double lf = l / 100.0;
        if (sf == 0) {
            int v = (int) Math.round(lf * 255);
            return new int[] {v, v, v};
        }
        double q = lf < 0.5 ? lf * (1 + sf) : lf + sf - lf * sf;
        double p = 2 * lf - q;
        double hf = h / 360.0;
        return new int[] {
                (int) Math.round(hueToChannel(p, q, hf + 1.0 / 3) * 255),
                (int) Math.round(hueToChannel(p, q, hf) * 255),
                (int) Math.round(hueToChannel(p, q, hf - 1.0 / 3) * 255)};
    }

    private static double hueToChannel(double p, double q, double tIn) {
        double t = tIn;
        if (t < 0) {
            t += 1;
        }
        if (t > 1) {
            t -= 1;
        }
        if (t < 1.0 / 6) {
            return p + (q - p) * 6 * t;
        }
        if (t < 1.0 / 2) {
            return q;
        }
        if (t < 2.0 / 3) {
            return p + (q - p) * (2.0 / 3 - t) * 6;
        }
        return p;
    }

    private static ColorConversionResponse buildResponse(int r, int g, int b) {
        String hex6 = "%02X%02X%02X".formatted(r, g, b);
        return new ColorConversionResponse(
                "#" + hex6,
                "rgb(%d, %d, %d)".formatted(r, g, b),
                toHsl(r, g, b),
                "--color: #%s;".formatted(hex6),
                toSwiftUIColor(r, g, b),
                "Color.rgb(%d, %d, %d)".formatted(r, g, b));
    }

    // Standard RGB -> HSL conversion (CSS Color Module's own algorithm), each component rounded to
    // the nearest whole number for display — hsl(173, 80%, 40%) for #14B8A6, confirmed against the
    // exact reported example before this was relied on.
    private static String toHsl(int r, int g, int b) {
        double rf = r / 255.0;
        double gf = g / 255.0;
        double bf = b / 255.0;
        double max = Math.max(rf, Math.max(gf, bf));
        double min = Math.min(rf, Math.min(gf, bf));
        double delta = max - min;

        double lightness = (max + min) / 2;
        double saturation = delta == 0 ? 0 : delta / (1 - Math.abs(2 * lightness - 1));

        double hue;
        if (delta == 0) {
            hue = 0;
        } else if (max == rf) {
            hue = 60 * (((gf - bf) / delta) % 6);
        } else if (max == gf) {
            hue = 60 * (((bf - rf) / delta) + 2);
        } else {
            hue = 60 * (((rf - gf) / delta) + 4);
        }
        if (hue < 0) {
            hue += 360;
        }

        return "hsl(%d, %d%%, %d%%)".formatted(Math.round(hue), Math.round(saturation * 100), Math.round(lightness * 100));
    }

    // 0-255 -> 0.0-1.0, rounded to 3 decimal places, Locale.ROOT so the decimal separator is
    // always "." regardless of the JVM's default locale — a real, easy-to-miss bug class in any
    // String.format call formatting a decimal for machine-readable output.
    private static String toSwiftUIColor(int r, int g, int b) {
        return "UIColor(red: %s, green: %s, blue: %s, alpha: 1)".formatted(
                toThreeDecimalFraction(r), toThreeDecimalFraction(g), toThreeDecimalFraction(b));
    }

    private static String toThreeDecimalFraction(int channel) {
        return String.format(Locale.ROOT, "%.3f", channel / 255.0);
    }
}
