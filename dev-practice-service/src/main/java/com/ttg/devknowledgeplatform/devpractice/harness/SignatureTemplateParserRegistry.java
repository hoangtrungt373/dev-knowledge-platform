package com.ttg.devknowledgeplatform.devpractice.harness;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * Selects the {@link SignatureTemplateParser} for a template's language — same shape as
 * {@link LanguageHarnessRegistry}. Fails at startup if any {@link ProgrammingLanguage} has no parser,
 * the same fail-fast rule {@code Judge0Client} applies to language ids: adding a language must add
 * its harness, its judge id, and its template parser together.
 */
@Component
public class SignatureTemplateParserRegistry {

    private final Map<ProgrammingLanguage, SignatureTemplateParser> parsers = new EnumMap<>(ProgrammingLanguage.class);

    public SignatureTemplateParserRegistry(List<SignatureTemplateParser> parsers) {
        parsers.forEach(p -> this.parsers.put(p.language(), p));
        for (ProgrammingLanguage language : ProgrammingLanguage.values()) {
            if (!this.parsers.containsKey(language)) {
                throw new IllegalStateException("No SignatureTemplateParser registered for " + language);
            }
        }
    }

    /**
     * @param language the template's language
     * @param code     the template's source
     * @return the signature it declares
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException {@code PROBLEM_TEMPLATE_INVALID}
     */
    public ParsedSignature parse(ProgrammingLanguage language, String code) {
        return parsers.get(language).parse(code);
    }
}
