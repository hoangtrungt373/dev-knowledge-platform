package com.ttg.devknowledgeplatform.devpractice.judge;

import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.enums.OutputOrder;
import com.ttg.devknowledgeplatform.devpractice.enums.ParamType;

/**
 * Everything {@link OutputMatcher} needs to know about a problem to decide whether an output is a
 * correct answer: the return type (exact vs. tolerant numbers) and whether order matters. One value
 * built from the problem in one place ({@link #of}), so no caller can pass one without the other.
 *
 * @param returnType  the problem's declared return type
 * @param outputOrder whether the answer's elements may come back in any order
 */
public record AnswerSpec(ParamType returnType, OutputOrder outputOrder) {

    /** The problem's own spec — the way every judging path should obtain one. */
    public static AnswerSpec of(Problem problem) {
        return new AnswerSpec(problem.getReturnType(), problem.getOutputOrder());
    }

    /** An order-sensitive spec — the default for every problem that doesn't say otherwise. */
    public static AnswerSpec ordered(ParamType returnType) {
        return new AnswerSpec(returnType, OutputOrder.ORDERED);
    }
}
