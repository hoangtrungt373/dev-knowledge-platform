package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.devutils.dto.StringCaseResponse;

class StringCaseConverterTest {

    @Test
    void convertsASpaceSeparatedSentenceIntoEveryCaseVariant() {
        StringCaseResponse result = StringCaseConverter.convert("Build ship and share with Vui Coding");

        assertThat(result.camelCase()).isEqualTo("buildShipAndShareWithVuiCoding");
        assertThat(result.pascalCase()).isEqualTo("BuildShipAndShareWithVuiCoding");
        assertThat(result.snakeCase()).isEqualTo("build_ship_and_share_with_vui_coding");
        assertThat(result.kebabCase()).isEqualTo("build-ship-and-share-with-vui-coding");
        assertThat(result.constantCase()).isEqualTo("BUILD_SHIP_AND_SHARE_WITH_VUI_CODING");
        assertThat(result.titleCase()).isEqualTo("Build Ship And Share With Vui Coding");
        assertThat(result.sentenceCase()).isEqualTo("Build ship and share with vui coding");
    }

    @Test
    void splitsCamelCaseInputOnLowerToUpperBoundaries() {
        StringCaseResponse result = StringCaseConverter.convert("buildShipAndShare");

        assertThat(result.snakeCase()).isEqualTo("build_ship_and_share");
        assertThat(result.titleCase()).isEqualTo("Build Ship And Share");
    }

    @Test
    void splitsAnAcronymRunFromAFollowingCapitalizedWord() {
        StringCaseResponse result = StringCaseConverter.convert("XMLHttpRequest");

        assertThat(result.snakeCase()).isEqualTo("xml_http_request");
        assertThat(result.titleCase()).isEqualTo("Xml Http Request");
    }

    @Test
    void splitsSnakeCaseAndKebabCaseInputOnDelimiters() {
        assertThat(StringCaseConverter.convert("build_ship_and_share").camelCase()).isEqualTo("buildShipAndShare");
        assertThat(StringCaseConverter.convert("build-ship-and-share").camelCase()).isEqualTo("buildShipAndShare");
    }

    @Test
    void roundTripsEachVariantBackToTheSameWords() {
        StringCaseResponse original = StringCaseConverter.convert("Build ship and share with Vui Coding");

        assertThat(StringCaseConverter.convert(original.camelCase()).snakeCase())
                .isEqualTo(original.snakeCase());
        assertThat(StringCaseConverter.convert(original.constantCase()).camelCase())
                .isEqualTo(original.camelCase());
        assertThat(StringCaseConverter.convert(original.kebabCase()).titleCase())
                .isEqualTo(original.titleCase());
    }

    @Test
    void neverThrowsOnPunctuationOnlyOrEmptyWordInput() {
        StringCaseResponse result = StringCaseConverter.convert("!!!");

        assertThat(result.camelCase()).isEmpty();
        assertThat(result.snakeCase()).isEmpty();
    }

    @Test
    void singleWordInputConvertsCorrectlyInEveryCase() {
        StringCaseResponse result = StringCaseConverter.convert("coding");

        assertThat(result.camelCase()).isEqualTo("coding");
        assertThat(result.pascalCase()).isEqualTo("Coding");
        assertThat(result.constantCase()).isEqualTo("CODING");
    }
}
