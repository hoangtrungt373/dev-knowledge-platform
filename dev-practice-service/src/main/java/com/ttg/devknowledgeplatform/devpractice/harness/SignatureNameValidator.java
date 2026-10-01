package com.ttg.devknowledgeplatform.devpractice.harness;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.exception.Validator;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;

/**
 * Checks that a problem's method name and parameter names will compile in <em>every</em> supported
 * language — a problem is shared by all of them, so a name only one language rejects (e.g.
 * {@code def}, valid Java but a Python keyword) still breaks that language's submissions. Three
 * rules:
 * <ol>
 *   <li><b>Shape</b> — {@link #IDENTIFIER_REGEX}: ASCII letter first, then letters/digits/{@code _}.
 *       The common subset of Java/Python/JavaScript identifiers. A leading {@code _} is rejected on
 *       purpose: the generated entry points name their own locals {@code _args}/{@code _result}/
 *       {@code _sol}, so the {@code _} prefix is the harnesses' private namespace and can never
 *       collide with a user-facing name.</li>
 *   <li><b>Not reserved</b> — by any registered {@link LanguageHarness#reservedNames()} (keywords
 *       plus names a harness's generated code depends on). Adding a language adds its rules here
 *       automatically, with no change to this class.</li>
 *   <li><b>Unique</b> — no two parameters share a name (a compile error in every language).</li>
 * </ol>
 *
 * <p>The request DTOs repeat rule 1 with {@code @Pattern(regexp = IDENTIFIER_REGEX)} for a fast
 * field-level 400; this class is the authoritative check, run by {@code ProblemServiceImpl} on every
 * create/update, and the only one covering rules 2–3.
 */
@Component
public class SignatureNameValidator {

    /** ASCII letter, then letters/digits/underscores — see the class Javadoc for why not {@code _} first. */
    public static final String IDENTIFIER_REGEX = "^[A-Za-z][A-Za-z0-9_]*$";

    private static final Pattern IDENTIFIER = Pattern.compile(IDENTIFIER_REGEX);

    private final List<LanguageHarness> harnesses;

    public SignatureNameValidator(List<LanguageHarness> harnesses) {
        this.harnesses = harnesses.stream()
                .sorted(Comparator.comparing(LanguageHarness::language))
                .toList();
    }

    /**
     * Validates one signature's names.
     *
     * @param methodName     the problem's method name
     * @param parameterNames its parameter names, in order
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException
     *         {@code PROBLEM_INVALID_IDENTIFIER} or {@code PROBLEM_DUPLICATE_PARAMETER_NAME}
     */
    public void validate(String methodName, List<String> parameterNames) {
        checkName(methodName, "method");
        Set<String> seen = new HashSet<>();
        for (String name : parameterNames) {
            checkName(name, "parameter");
            // (Object) cast: a lone String argument would bind to Validator's isTrue(..., String message)
            // overload and replace the error code's template instead of filling its {0}.
            Validator.isTrue(seen.add(name), DevPracticeErrorCode.PROBLEM_DUPLICATE_PARAMETER_NAME, (Object) name);
        }
    }

    private void checkName(String name, String kind) {
        Validator.isTrue(name != null && IDENTIFIER.matcher(name).matches(),
                DevPracticeErrorCode.PROBLEM_INVALID_IDENTIFIER, name, kind,
                "use ASCII letters, digits and _, starting with a letter");
        String reservedIn = harnesses.stream()
                .filter(h -> h.reservedNames().contains(name))
                .map(h -> h.language().name())
                .collect(Collectors.joining(", "));
        Validator.isTrue(reservedIn.isEmpty(), DevPracticeErrorCode.PROBLEM_INVALID_IDENTIFIER, name, kind,
                "it is reserved in " + reservedIn);
    }
}
