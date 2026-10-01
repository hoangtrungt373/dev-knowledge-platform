package com.ttg.devknowledgeplatform.devpractice.mapper;

import java.util.Comparator;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.devpractice.dto.AdminProblemSummaryResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.MethodParameterResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ParsedSignatureResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSummaryResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemTagResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemTagSummaryResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.TestCaseResponse;
import com.ttg.devknowledgeplatform.devpractice.entity.MethodParameter;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTag;
import com.ttg.devknowledgeplatform.devpractice.entity.TestCase;
import com.ttg.devknowledgeplatform.devpractice.harness.ParsedSignature;

@Mapper(componentModel = "spring")
public interface ProblemMapper {

    @Mapping(target = "createdAt", source = "dteCreation")
    @Mapping(target = "tags", expression = "java(tagsOf(problem))")
    ProblemResponse toResponse(Problem problem);

    @Mapping(target = "tags", expression = "java(tagsOf(problem))")
    ProblemSummaryResponse toSummaryResponse(Problem problem);

    @Mapping(target = "createdAt", source = "dteCreation")
    @Mapping(target = "tags", expression = "java(tagsOf(problem))")
    AdminProblemSummaryResponse toAdminSummaryResponse(Problem problem);

    TestCaseResponse toResponse(TestCase testCase);

    MethodParameterResponse toResponse(MethodParameter parameter);

    @Mapping(target = "createdAt", source = "dteCreation")
    ProblemTagResponse toResponse(ProblemTag tag);

    ProblemTagSummaryResponse toSummary(ProblemTag tag);

    @Mapping(target = "returnType", source = "returnType.type")
    @Mapping(target = "returnTypeAlternatives", source = "returnType.alternatives")
    ParsedSignatureResponse toResponse(ParsedSignature signature);

    @Mapping(target = "type", source = "type.type")
    @Mapping(target = "alternatives", source = "type.alternatives")
    ParsedSignatureResponse.Parameter toResponse(ParsedSignature.ParsedParameter parameter);

    /** A problem's tags, flattened out of its assignment rows and sorted by name for stable display. */
    default List<ProblemTagSummaryResponse> tagsOf(Problem problem) {
        return problem.getTagAssignments().stream()
                .map(assignment -> toSummary(assignment.getProblemTag()))
                .sorted(Comparator.comparing(ProblemTagSummaryResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * Same as {@link #toResponse(Problem)}, but with hidden test cases stripped — the shape a
     * public, unauthenticated caller is allowed to see. Never expose a {@code sample = false}
     * test case's expected output outside {@code /api/v1/admin/**}.
     */
    default ProblemResponse toPublicResponse(Problem problem) {
        ProblemResponse full = toResponse(problem);
        List<TestCaseResponse> sampleOnly = full.testCases().stream()
                .filter(TestCaseResponse::sample)
                .toList();
        return new ProblemResponse(full.id(), full.slug(), full.title(), full.description(),
                full.difficulty(), full.status(), full.methodName(), full.returnType(),
                full.parameters(), sampleOnly, full.tags(), full.publishedAt(), full.createdAt());
    }
}
