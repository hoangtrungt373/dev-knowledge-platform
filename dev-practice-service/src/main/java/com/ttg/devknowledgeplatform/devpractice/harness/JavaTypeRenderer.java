package com.ttg.devknowledgeplatform.devpractice.harness;

import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

/**
 * Java type spellings, each paired with the {@code JsonMini} converter that turns a parsed JSON
 * value into that native type (see {@code harness/java/JsonMini.java}). The JSON-<em>write</em>
 * side needs no entry here: {@code JsonMini.write} is overloaded per type, so Java's own overload
 * resolution picks the right one from the declared return type.
 */
final class JavaTypeRenderer implements TypeRenderer {

    @Override
    public TypeSyntax render(ParamType type) {
        return switch (type) {
            case INT -> new TypeSyntax("int", "toInt");
            case LONG -> new TypeSyntax("long", "toLong");
            case DOUBLE -> new TypeSyntax("double", "toDouble");
            case BOOLEAN -> new TypeSyntax("boolean", "toBool");
            case STRING -> new TypeSyntax("String", "toStr");
            case INT_ARRAY -> new TypeSyntax("int[]", "toIntArray");
            case DOUBLE_ARRAY -> new TypeSyntax("double[]", "toDoubleArray");
            case BOOLEAN_ARRAY -> new TypeSyntax("boolean[]", "toBooleanArray");
            case STRING_ARRAY -> new TypeSyntax("String[]", "toStringArray");
            case INT_MATRIX -> new TypeSyntax("int[][]", "toIntMatrix");
        };
    }
}
