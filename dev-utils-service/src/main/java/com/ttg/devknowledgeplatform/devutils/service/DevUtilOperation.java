package com.ttg.devknowledgeplatform.devutils.service;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;

/**
 * Strategy contract for a single dev-utils text transformation (format, validate-and-format, or
 * convert between formats).
 *
 * <p><b>Design pattern: Strategy.</b> One implementation per operation
 * ({@code service.impl.JsonFormatOperation}, {@code YamlToJsonOperation}, {@code
 * JsonToYamlOperation}, {@code HtmlBeautifyOperation}), each its own Spring bean, injected
 * directly by concrete type into {@code api.impl.DevUtilsController} (one fixed REST endpoint per
 * operation, so there is no runtime dispatch-by-key to justify an enum-keyed registry on top of
 * this). The payoff is Open/Closed: a future operation (Base64 encode/decode, UUID generation,
 * regex test, JWT decode, ...) is a new class implementing this interface plus one new controller
 * method — zero changes to any existing implementation. The alternative considered was a single
 * flat {@code DevUtilsService} facade with one method per operation and no shared interface — less
 * ceremony for exactly these three conversions, but every future addition would touch that one
 * facade class directly instead of just adding a file, which matters here given more tools are a
 * likely next step for this module (JSON/YAML/HTML today, a broader utility belt discussed as the
 * explicit direction).
 */
public interface DevUtilOperation {

    /**
     * Transforms {@code input} into this operation's output format.
     *
     * @param input raw text to transform
     * @return the transformed text
     * @throws BusinessException if {@code input} is not valid for this operation's source format
     */
    String execute(String input);
}
