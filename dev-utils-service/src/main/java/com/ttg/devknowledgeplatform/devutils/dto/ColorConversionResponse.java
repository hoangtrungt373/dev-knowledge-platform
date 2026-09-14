package com.ttg.devknowledgeplatform.devutils.dto;

/**
 * Mirrors {@code StringCaseResponse}/{@code HashResponse}/{@code TimestampResponse}'s own shape —
 * one operation whose output is genuinely richer than a single string, so it gets its own response
 * type rather than being forced into {@link DevUtilResponse} (see that record's own Javadoc).
 * {@code ColorConverterOperation}'s own six representations of the same color at once.
 *
 * @param hex         normalized {@code #RRGGBB}, uppercase, regardless of how the input was cased
 *                     or whether it used the 3-digit shorthand
 * @param rgb         {@code rgb(R, G, B)}
 * @param hsl         {@code hsl(H, S%, L%)}, each component rounded to the nearest integer
 * @param cssVariable {@code --color: #RRGGBB;} — a fixed, generic variable name (per direct
 *                     request; not derived or guessable from a hex value alone, so this operation
 *                     doesn't try — the caller renames it themselves when pasting)
 * @param swift       {@code UIColor(red: R, green: G, blue: B, alpha: 1)}, each channel scaled to
 *                     {@code 0.0}-{@code 1.0} and rounded to 3 decimal places; alpha is always the
 *                     literal {@code 1} — this operation's input has no alpha channel of its own
 * @param android     {@code Color.rgb(R, G, B)} ({@code android.graphics.Color}'s own int-RGB
 *                     factory)
 */
public record ColorConversionResponse(String hex, String rgb, String hsl, String cssVariable, String swift, String android) {
}
