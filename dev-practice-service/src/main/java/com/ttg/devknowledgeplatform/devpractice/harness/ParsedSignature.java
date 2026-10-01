package com.ttg.devknowledgeplatform.devpractice.harness;

import java.util.List;

import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

/**
 * A method signature read out of a code template by a {@link SignatureTemplateParser}.
 *
 * @param methodName the template's method name
 * @param returnType the return type, plus any equally-valid alternatives
 * @param parameters the parameters, in declaration order
 */
public record ParsedSignature(String methodName, TypeChoice returnType, List<ParsedParameter> parameters) {

    /**
     * One parsed type. {@code alternatives} is non-empty when the template's own spelling can't
     * tell types apart — Python's {@code int} is both {@code INT} and {@code LONG}; JavaScript's
     * {@code number[]} is both {@code INT_ARRAY} and {@code DOUBLE_ARRAY}. {@code type} is then the
     * most common choice (the first in {@link ParamType}'s declaration order) and the admin is asked
     * to confirm it. A Java template never has alternatives: Java spells every type exactly.
     *
     * @param type         the chosen type
     * @param alternatives other types the same spelling could mean, excluding {@code type}
     */
    public record TypeChoice(ParamType type, List<ParamType> alternatives) {
    }

    /**
     * @param name the parameter's name
     * @param type its parsed type
     */
    public record ParsedParameter(String name, TypeChoice type) {
    }
}
