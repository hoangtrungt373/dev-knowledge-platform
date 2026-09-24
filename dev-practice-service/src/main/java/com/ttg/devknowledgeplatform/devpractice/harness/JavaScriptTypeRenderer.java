package com.ttg.devknowledgeplatform.devpractice.harness;

import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

/**
 * JSDoc type names, used only in the starter code's doc comment — JavaScript signatures carry no
 * types. No converters: {@code JSON.parse} already yields native values.
 */
final class JavaScriptTypeRenderer implements TypeRenderer {

    @Override
    public TypeSyntax render(ParamType type) {
        return switch (type) {
            case INT, LONG, DOUBLE -> TypeSyntax.declarationOnly("number");
            case BOOLEAN -> TypeSyntax.declarationOnly("boolean");
            case STRING -> TypeSyntax.declarationOnly("string");
            case INT_ARRAY, DOUBLE_ARRAY -> TypeSyntax.declarationOnly("number[]");
            case BOOLEAN_ARRAY -> TypeSyntax.declarationOnly("boolean[]");
            case STRING_ARRAY -> TypeSyntax.declarationOnly("string[]");
            case INT_MATRIX -> TypeSyntax.declarationOnly("number[][]");
        };
    }
}
