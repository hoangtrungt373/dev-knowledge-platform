package com.ttg.devknowledgeplatform.devpractice.harness;

import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Python is the simplest of the three harnesses — {@code json}/{@code typing} are both stdlib, so
 * no embedded (de)serialization helper is needed (contrast {@link JavaLanguageHarness}'s
 * hand-rolled {@code JsonMini}).
 */
@Component
public class PythonLanguageHarness extends LanguageHarness {

    @Override
    public ProgrammingLanguage language() {
        return ProgrammingLanguage.PYTHON;
    }

    @Override
    public String renderStarterCode(Problem problem) {
        String params = problem.getParameters().stream()
                .map(p -> p.getName() + ": " + typeHint(p.getType()))
                .collect(Collectors.joining(", "));
        return """
                from typing import List


                class Solution:
                    def %s(self, %s) -> %s:
                        pass
                """.formatted(problem.getMethodName(), params, typeHint(problem.getReturnType()));
    }

    @Override
    protected String renderPrelude() {
        return "from typing import List\n\n\n";
    }

    @Override
    protected String renderMain(Problem problem) {
        return """


                if __name__ == "__main__":
                    import json, sys
                    _args = json.loads(sys.stdin.read())
                    _result = Solution().%s(*_args)
                    print(json.dumps(_result))
                """.formatted(problem.getMethodName());
    }

    private static String typeHint(ParamType type) {
        return switch (type) {
            case INT, LONG -> "int";
            case DOUBLE -> "float";
            case BOOLEAN -> "bool";
            case STRING -> "str";
            case INT_ARRAY -> "List[int]";
            case DOUBLE_ARRAY -> "List[float]";
            case BOOLEAN_ARRAY -> "List[bool]";
            case STRING_ARRAY -> "List[str]";
            case INT_MATRIX -> "List[List[int]]";
        };
    }
}
