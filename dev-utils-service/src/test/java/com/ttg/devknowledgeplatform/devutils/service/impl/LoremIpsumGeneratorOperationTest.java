package com.ttg.devknowledgeplatform.devutils.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;

/**
 * Every assertion here checks the generated text's own *shape* (paragraph count, non-blank
 * paragraphs, the classic opening sentence, sentences ending in a period) rather than its exact
 * wording — the output is genuinely randomized per call (see
 * {@code LoremIpsumGeneratorOperation}'s own Javadoc for why that's deliberate, not an
 * oversight), so a byte-for-byte expected string would be either flaky or would have to fake
 * determinism this operation was never meant to have.
 */
class LoremIpsumGeneratorOperationTest {

    private final LoremIpsumGeneratorOperation operation = new LoremIpsumGeneratorOperation();

    @Test
    void declaresTheGeneratorsGroup() {
        assertThat(operation.group()).isEqualTo(OperationGroup.GENERATORS);
    }

    @Test
    void generatesExactlyOneParagraphWhenRequested() {
        String result = operation.execute(1);

        assertThat(result.split("\n\n")).hasSize(1);
    }

    @Test
    void generatesExactlyTheRequestedNumberOfParagraphsAcrossTheFullRange() {
        for (int paragraphs : new int[] {1, 3, 5, 10, 20}) {
            String result = operation.execute(paragraphs);

            String[] parts = result.split("\n\n");
            assertThat(parts).as("paragraph count for input %d", paragraphs).hasSize(paragraphs);
            assertThat(parts).allSatisfy(paragraph -> assertThat(paragraph).isNotBlank());
        }
    }

    @Test
    void firstParagraphAlwaysOpensWithTheClassicLoremIpsumSentence() {
        String result = operation.execute(5);

        String firstParagraph = result.split("\n\n")[0];
        assertThat(firstParagraph).startsWith("Lorem ipsum dolor sit amet, consectetur adipiscing elit.");
    }

    @Test
    void everyParagraphIsMadeOfRealSentencesEndingInAPeriod() {
        String result = operation.execute(4);

        for (String paragraph : result.split("\n\n")) {
            assertThat(paragraph).endsWith(".");
            // Every sentence inside a paragraph starts with an uppercase letter — a real
            // structural guarantee (Character.toUpperCase on each sentence's first letter), not
            // an incidental property of the random word pool.
            for (String sentence : paragraph.split("(?<=\\.)\\s+")) {
                assertThat(sentence).matches("^[A-Z].*\\.$");
            }
        }
    }

    @Test
    void subsequentParagraphsDoNotRepeatTheClassicOpening() {
        String result = operation.execute(3);

        String[] paragraphs = result.split("\n\n");
        for (int i = 1; i < paragraphs.length; i++) {
            assertThat(paragraphs[i]).doesNotStartWith("Lorem ipsum dolor sit amet, consectetur adipiscing elit.");
        }
    }
}
