package com.ttg.devknowledgeplatform.devutils.dto;

/**
 * Response body for the String Case Converter — genuinely richer than the single-string
 * {@link DevUtilResponse} every other operation shares, so it gets its own type rather than being
 * forced into that one, exactly the scenario {@link DevUtilResponse}'s own Javadoc anticipates.
 *
 * @param camelCase    e.g. {@code buildShipAndShareWithDevKnowledge}
 * @param pascalCase   e.g. {@code BuildShipAndShareWithDevKnowledge}
 * @param snakeCase    e.g. {@code build_ship_and_share_with_dev_knowledge}
 * @param kebabCase    e.g. {@code build-ship-and-share-with-dev-knowledge}
 * @param constantCase e.g. {@code BUILD_SHIP_AND_SHARE_WITH_DEV_KNOWLEDGE}
 * @param titleCase    e.g. {@code Build Ship And Share With Dev Knowledge}
 * @param sentenceCase e.g. {@code Build ship and share with dev knowledge}
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
