package com.ttg.devknowledgeplatform.devpractice.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.devpractice.dto.ProblemResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSummaryResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.TestCaseResponse;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.TestCase;

@Mapper(componentModel = "spring")
public interface ProblemMapper {

    @Mapping(target = "createdAt", source = "dteCreation")
    ProblemResponse toResponse(Problem problem);

    ProblemSummaryResponse toSummaryResponse(Problem problem);

    TestCaseResponse toResponse(TestCase testCase);

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
                full.difficulty(), full.status(), sampleOnly, full.publishedAt(), full.createdAt());
    }
}
