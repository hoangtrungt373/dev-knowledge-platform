package com.ttg.devknowledgeplatform.devpractice.mapper;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.devpractice.dto.AdminProblemSolutionResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.AdminSolutionCodeResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSolutionResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.SolutionCodeResponse;
import com.ttg.devknowledgeplatform.devpractice.editorial.CodePlaceholders;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemSolution;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemSolutionCode;
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

    /**
     * The learner view. {@code preferredLanguage} ({@code @Context}: available to the expression, never
     * treated as a source of properties) switches the explanation's code to that language where the
     * approach has it — see {@link CodePlaceholders#render(String, Map, ProgrammingLanguage)}; null keeps
     * the explanation as written.
     */
    @Mapping(target = "explanation", expression = "java(CodePlaceholders.render(solution.getExplanation(), verifiedCodeByLanguage(solution), preferredLanguage))")
    @Mapping(target = "codes", expression = "java(solution.verifiedCode().stream().map(this::toCodeResponse).toList())")
    ProblemSolutionResponse toResponse(ProblemSolution solution, @Context ProgrammingLanguage preferredLanguage);

    SolutionCodeResponse toCodeResponse(Submission run);

    /**
     * The admin editor's view: the stored explanation (placeholders intact) plus its learner rendering,
     * and every linked run — flagged {@code verified} when learners currently see it.
     */
    @Mapping(target = "renderedExplanation", expression = "java(CodePlaceholders.render(solution.getExplanation(), verifiedCodeByLanguage(solution)))")
    @Mapping(target = "codes", expression = "java(adminCodes(solution))")
    AdminProblemSolutionResponse toAdminResponse(ProblemSolution solution);

    /** Every linked run, ordered by language, with whether it's among the approach's verified code. */
    default List<AdminSolutionCodeResponse> adminCodes(ProblemSolution solution) {
        List<Submission> verified = solution.verifiedCode();
        return solution.getCodes().stream()
                .map(ProblemSolutionCode::getSubmission)
                .sorted(Comparator.comparing(Submission::getLanguage))
                .map(run -> new AdminSolutionCodeResponse(run.getId(), run.getLanguage(), run.getStatus(),
                        run.getContractVersion(), verified.contains(run), run.getRuntimeMs(), run.getMemoryKb()))
                .toList();
    }

    /** Each verified language's source — the only code a placeholder may expand to. */
    default Map<ProgrammingLanguage, String> verifiedCodeByLanguage(ProblemSolution solution) {
        Map<ProgrammingLanguage, String> byLanguage = new EnumMap<>(ProgrammingLanguage.class);
        solution.verifiedCode().forEach(run -> byLanguage.put(run.getLanguage(), run.getSourceCode()));
        return byLanguage;
    }
}
