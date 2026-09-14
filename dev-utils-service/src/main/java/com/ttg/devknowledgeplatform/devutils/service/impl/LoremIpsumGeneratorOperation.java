package com.ttg.devknowledgeplatform.devutils.service.impl;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devutils.service.DevUtilOperation;
import com.ttg.devknowledgeplatform.devutils.service.OperationGroup;
import com.ttg.devknowledgeplatform.devutils.service.impl.support.LoremIpsumWords;

/**
 * Generates classic "Lorem Ipsum" placeholder text — {@link OperationGroup#GENERATORS}'s own
 * worked example, named directly in that enum's own Javadoc ("a future UUID/Lorem Ipsum
 * generator"). Unlike every operation above it in this package, this one *produces* new content
 * rather than transforming an existing input — its only parameter is how many paragraphs to
 * generate (validated 1-20 by {@code dto.LoremIpsumRequest} before this is ever called).
 *
 * <p>The very first sentence is always the traditional "Lorem ipsum dolor sit amet, consectetur
 * adipiscing elit." — the one universally recognized opening every real Lorem Ipsum generator
 * (lipsum.com included) uses, so the output is immediately recognizable as placeholder text
 * rather than a random word salad. Every sentence after that (including the rest of paragraph
 * one) is freshly assembled from a fixed pool of genuine Latin filler words
 * ({@link LoremIpsumWords#WORDS}) — plain {@link SecureRandom}, not seeded, since this operation
 * makes no correctness claim about reproducing a specific output, only about shape (the requested
 * paragraph count, and each paragraph being a handful of real sentences) — see
 * {@code LoremIpsumGeneratorOperationTest}'s own note on why its assertions check structure, not
 * exact wording.
 */
@Component
public class LoremIpsumGeneratorOperation implements DevUtilOperation {

    /** Inclusive bounds on one paragraph's own sentence count — a real Lorem Ipsum paragraph
     * (lipsum.com's own generator included) reads as a handful of sentences, not a single one and
     * not fifty. */
    private static final int MIN_SENTENCES_PER_PARAGRAPH = 3;
    private static final int MAX_SENTENCES_PER_PARAGRAPH = 7;
    /** Inclusive bounds on one sentence's own word count. */
    private static final int MIN_WORDS_PER_SENTENCE = 6;
    private static final int MAX_WORDS_PER_SENTENCE = 18;

    private static final String CLASSIC_OPENING = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";
    /** Paragraphs are blank-line separated — the same plain-text convention every other
     * multi-paragraph/multi-value dev-utils response already uses (e.g. the GUI's own
     * {@code formatHashResult}/{@code formatStringCaseResult}). */
    private static final String PARAGRAPH_SEPARATOR = "\n\n";

    private final Random random = new SecureRandom();

    @Override
    public OperationGroup group() {
        return OperationGroup.GENERATORS;
    }

    /**
     * @param paragraphs how many paragraphs to generate — already validated to be 1-20 by
     *                   {@code dto.LoremIpsumRequest}, so this method trusts the value rather than
     *                   re-checking it.
     * @return {@code paragraphs} paragraphs of placeholder text, separated by a blank line
     */
    public String execute(int paragraphs) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < paragraphs; i++) {
            if (i > 0) {
                result.append(PARAGRAPH_SEPARATOR);
            }
            result.append(generateParagraph(i == 0));
        }
        return result.toString();
    }

    private String generateParagraph(boolean isFirst) {
        int sentenceCount = randomBetween(MIN_SENTENCES_PER_PARAGRAPH, MAX_SENTENCES_PER_PARAGRAPH);
        StringBuilder paragraph = new StringBuilder();
        if (isFirst) {
            paragraph.append(CLASSIC_OPENING);
            sentenceCount--;
        }
        for (int i = 0; i < sentenceCount; i++) {
            if (paragraph.length() > 0) {
                paragraph.append(' ');
            }
            paragraph.append(generateSentence());
        }
        return paragraph.toString();
    }

    private String generateSentence() {
        int wordCount = randomBetween(MIN_WORDS_PER_SENTENCE, MAX_WORDS_PER_SENTENCE);
        StringBuilder sentence = new StringBuilder();
        for (int i = 0; i < wordCount; i++) {
            if (i > 0) {
                sentence.append(' ');
            }
            sentence.append(randomWord());
        }
        sentence.setCharAt(0, Character.toUpperCase(sentence.charAt(0)));
        sentence.append('.');
        return sentence.toString();
    }

    private String randomWord() {
        List<String> words = LoremIpsumWords.WORDS;
        return words.get(random.nextInt(words.size()));
    }

    private int randomBetween(int minInclusive, int maxInclusive) {
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }
}
