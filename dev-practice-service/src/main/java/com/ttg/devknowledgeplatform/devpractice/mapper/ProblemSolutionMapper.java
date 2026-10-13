package com.ttg.devknowledgeplatform.devpractice.mapper;

import java.util.EnumMap;
import java.util.Map;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSolutionResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.SolutionCodeResponse;
import com.ttg.devknowledgeplatform.devpractice.editorial.CodePlaceholders;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemSolution;
import com.ttg.devknowledgeplatform.devpractice.entity.Submission;
import com.ttg.devknowledgeplatform.devpractice.enums.ProgrammingLanguage;

/**
 * Editorial approach → learner DTO. {@code codes} always goes through
 * {@link ProblemSolution#verifiedCode()} — never the raw links — so a pending, rejected or stale run's
 * code can't reach a learner; the explanation's {@code {{code:LANGUAGE}}} placeholders are expanded
 * from that same verified code ({@link CodePlaceholders#render}), so the prose and the code panel
 * can never show different code.
 */
// imports: the generated implementation doesn't inherit this file's imports, and the expression uses it.
@Mapper(componentModel = "spring", imports = CodePlaceholders.class)
public interface ProblemSolutionMapper {

    @Mapping(target = "explanation", expression = "java(CodePlaceholders.render(solution.getExplanation(), verifiedCodeByLanguage(solution)))")
    @Mapping(target = "codes", expression = "java(solution.verifiedCode().stream().map(this::toCodeResponse).toList())")
    ProblemSolutionResponse toResponse(ProblemSolution solution);

    SolutionCodeResponse toCodeResponse(Submission run);

    /** Each verified language's source — the only code a placeholder may expand to. */
    default Map<ProgrammingLanguage, String> verifiedCodeByLanguage(ProblemSolution solution) {
        Map<ProgrammingLanguage, String> byLanguage = new EnumMap<>(ProgrammingLanguage.class);
        solution.verifiedCode().forEach(run -> byLanguage.put(run.getLanguage(), run.getSourceCode()));
        return byLanguage;
    }
}
