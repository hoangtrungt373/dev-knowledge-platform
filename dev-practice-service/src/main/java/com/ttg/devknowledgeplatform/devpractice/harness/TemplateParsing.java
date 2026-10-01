package com.ttg.devknowledgeplatform.devpractice.harness;

import java.util.ArrayList;
import java.util.List;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;

/** Small helpers shared by the {@link SignatureTemplateParser} implementations. */
final class TemplateParsing {

    private TemplateParsing() {
    }

    /**
     * @param reason what's wrong with the template, phrased as something the admin can fix
     * @return the exception to throw
     */
    static BusinessException invalid(String reason) {
        // (Object) cast: a lone String would bind to BusinessException(ErrorCode, String message)
        // and replace the template instead of filling its {0}.
        return new BusinessException(DevPracticeErrorCode.PROBLEM_TEMPLATE_INVALID, (Object) reason);
    }

    /**
     * Splits on commas that aren't nested inside {@code [] () <> {}} — so {@code a: List[int], b: int}
     * gives two parts. Blank parts (a trailing comma, an empty list) are dropped.
     */
    static List<String> splitTopLevel(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[' || c == '(' || c == '<' || c == '{') {
                depth++;
            } else if (c == ']' || c == ')' || c == '>' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts.stream().map(String::strip).filter(p -> !p.isEmpty()).toList();
    }
}
