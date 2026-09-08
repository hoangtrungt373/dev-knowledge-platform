package com.ttg.devknowledgeplatform.devutils.dto;

/**
 * Response body for the String Case Converter — genuinely richer than the single-string
 * {@link DevUtilResponse} every other operation shares, so it gets its own type rather than being
 * forced into that one, exactly the scenario {@link DevUtilResponse}'s own Javadoc anticipates.
 *
 * @param camelCase    e.g. {@code buildShipAndShareWithVuiCoding}
 * @param pascalCase   e.g. {@code BuildShipAndShareWithVuiCoding}
 * @param snakeCase    e.g. {@code build_ship_and_share_with_vui_coding}
 * @param kebabCase    e.g. {@code build-ship-and-share-with-vui-coding}
 * @param constantCase e.g. {@code BUILD_SHIP_AND_SHARE_WITH_VUI_CODING}
 * @param titleCase    e.g. {@code Build Ship And Share With Vui Coding}
 * @param sentenceCase e.g. {@code Build ship and share with vui coding}
 */
public record StringCaseResponse(
        String camelCase,
        String pascalCase,
        String snakeCase,
        String kebabCase,
        String constantCase,
        String titleCase,
        String sentenceCase) {
}
