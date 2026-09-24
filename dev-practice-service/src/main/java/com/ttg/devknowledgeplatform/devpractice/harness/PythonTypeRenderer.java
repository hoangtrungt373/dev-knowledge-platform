package com.ttg.devknowledgeplatform.devpractice.harness;

import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

/**
 * Python type hints ({@code typing.List}-style, which runs on Judge0's Python 3.8 — the built-in
 * {@code list[int]} generic syntax needs 3.9+). No converters: {@code json.loads} already yields
 * native values.
 */
final class PythonTypeRenderer implements TypeRenderer {

    @Override
    public TypeSyntax render(ParamType type) {
        return switch (type) {
            case INT, LONG -> TypeSyntax.declarationOnly("int");
            case DOUBLE -> TypeSyntax.declarationOnly("float");
            case BOOLEAN -> TypeSyntax.declarationOnly("bool");
            case STRING -> TypeSyntax.declarationOnly("str");
            case INT_ARRAY -> TypeSyntax.declarationOnly("List[int]");
            case DOUBLE_ARRAY -> TypeSyntax.declarationOnly("List[float]");
            case BOOLEAN_ARRAY -> TypeSyntax.declarationOnly("List[bool]");
            case STRING_ARRAY -> TypeSyntax.declarationOnly("List[str]");
            case INT_MATRIX -> TypeSyntax.declarationOnly("List[List[int]]");
        };
    }
}
