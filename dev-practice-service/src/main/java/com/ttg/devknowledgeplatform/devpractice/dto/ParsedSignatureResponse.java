package com.ttg.devknowledgeplatform.devpractice.dto;

import java.util.List;

import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

/**
 * The signature read out of a code template, for the admin form to fill its signature editor with.
 * A non-empty {@code ...Alternatives} list means the template's spelling was ambiguous (Python
 * {@code int}, JavaScript {@code number}/{@code number[]}) and the chosen type should be confirmed.
 *
 * @param methodName             the method name
 * @param returnType             the chosen return type
 * @param returnTypeAlternatives other types the return spelling could mean
 * @param parameters             the parameters, in order
 */
public record ParsedSignatureResponse(
        String methodName,
        ParamType returnType,
        List<ParamType> returnTypeAlternatives,
        List<Parameter> parameters) {

    /**
     * @param name         parameter name
     * @param type         chosen type
     * @param alternatives other types the spelling could mean
     */
    public record Parameter(String name, ParamType type, List<ParamType> alternatives) {
    }
}
