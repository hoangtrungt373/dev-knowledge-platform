package com.ttg.devknowledgeplatform.devpractice.harness;

import com.ttg.devknowledgeplatform.devpractice.entity.MethodParameter;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * JavaScript (Node.js), the second-simplest harness — {@code JSON.parse}/{@code JSON.stringify}
 * are native, so no embedded (de)serialization helper is needed (contrast
 * {@link JavaLanguageHarness}'s hand-rolled {@code JsonMini}). Follows real LeetCode's own JS
 * convention: a {@code var fn = function(...) {}} expression, not a class.
 */
@Component
public class JavaScriptLanguageHarness extends LanguageHarness {

    @Override
    public ProgrammingLanguage language() {
        return ProgrammingLanguage.JAVASCRIPT;
    }

    @Override
    public String renderStarterCode(Problem problem) {
        String params = problem.getParameters().stream()
                .map(MethodParameter::getName)
                .collect(Collectors.joining(", "));
        return """
                /**
                %s
                 */
                var %s = function(%s) {

                };
                """.formatted(jsdocParams(problem), problem.getMethodName(), params);
    }

    @Override
    protected String renderPrelude() {
        return "";
    }

    @Override
    protected String renderMain(Problem problem) {
        return """


                const _args = JSON.parse(require('fs').readFileSync(0, 'utf8'));
                const _result = %s(..._args);
                console.log(JSON.stringify(_result));
                """.formatted(problem.getMethodName());
    }

    private static String jsdocParams(Problem problem) {
        StringBuilder sb = new StringBuilder();
        for (var p : problem.getParameters()) {
            sb.append(" * @param {").append(jsType(p.getType())).append("} ").append(p.getName()).append('\n');
        }
        sb.append(" * @return {").append(jsType(problem.getReturnType())).append('}');
        return sb.toString();
    }

    private static String jsType(ParamType type) {
        return switch (type) {
            case INT, LONG, DOUBLE -> "number";
            case BOOLEAN -> "boolean";
            case STRING -> "string";
            case INT_ARRAY, DOUBLE_ARRAY -> "number[]";
            case BOOLEAN_ARRAY -> "boolean[]";
            case STRING_ARRAY -> "string[]";
            case INT_MATRIX -> "number[][]";
        };
    }
}
