package com.ttg.devknowledgeplatform.devpractice.harness;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * Selects the {@link LanguageHarness} matching a submission's declared {@link ProgrammingLanguage}
 * — the Strategy half of this module's harness design (see {@link LanguageHarness}'s own Javadoc
 * for the Template Method half). Spring supplies every {@code @Component}-annotated
 * {@code LanguageHarness} subclass automatically; adding a new language means adding one more such
 * bean plus a matching {@link ProgrammingLanguage} enum constant — no change here.
 */
@Component
public class LanguageHarnessRegistry {

    private final Map<ProgrammingLanguage, LanguageHarness> harnessesByLanguage;

    public LanguageHarnessRegistry(List<LanguageHarness> harnesses) {
        this.harnessesByLanguage = harnesses.stream()
                .collect(Collectors.toUnmodifiableMap(LanguageHarness::language, Function.identity()));
    }

    /**
     * Returns the harness for the given language.
     *
     * @param language the submission's declared language
     * @return the matching harness
     * @throws IllegalStateException if no {@link LanguageHarness} bean declares this language —
     *         a configuration error (a {@link ProgrammingLanguage} constant with no matching
     *         harness bean), not a user-facing one
     */
    public LanguageHarness get(ProgrammingLanguage language) {
        LanguageHarness harness = harnessesByLanguage.get(language);
        if (harness == null) {
            throw new IllegalStateException("No LanguageHarness registered for " + language);
        }
        return harness;
    }
}
