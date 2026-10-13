package com.ttg.devknowledgeplatform.devpractice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.devpractice.dto.StudyPlanProblemRefResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.StudyPlanSummaryResponse;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.StudyPlan;
import com.ttg.devknowledgeplatform.devpractice.entity.StudyPlanSection;

/**
 * The overview card's problem list: published problems only, in plan order across sections — what
 * the GUI's progress count and "Continue" button both rely on. (The summary mapping never touches
 * {@code ProblemMapper}, so the generated implementation runs here without Spring.)
 */
class StudyPlanMapperTest {

    private final StudyPlanMapper mapper = new StudyPlanMapperImpl();

    @Test
    void summaryListsPublishedProblemsInPlanOrderAcrossSections() {
        StudyPlan plan = StudyPlan.builder().name("NeetCode 150").slug("neetcode-150").position(1).build();
        StudyPlanSection arrays = plan.addSection("Arrays & Hashing", null);
        arrays.addProblem(problem(1, "contains-duplicate", ContentStatus.PUBLISHED));
        arrays.addProblem(problem(2, "valid-anagram", ContentStatus.DRAFT));
        StudyPlanSection stack = plan.addSection("Stack", null);
        stack.addProblem(problem(3, "evaluate-reverse-polish-notation", ContentStatus.PUBLISHED));

        StudyPlanSummaryResponse summary = mapper.toSummaryResponse(plan);

        assertThat(summary.slug()).isEqualTo("neetcode-150");
        assertThat(summary.sectionCount()).isEqualTo(2);
        assertThat(summary.problems()).containsExactly(
                new StudyPlanProblemRefResponse(1, "contains-duplicate"),
                new StudyPlanProblemRefResponse(3, "evaluate-reverse-polish-notation"));
    }

    private static Problem problem(int id, String slug, ContentStatus status) {
        Problem problem = Problem.builder().slug(slug).status(status).build();
        problem.setId(id);
        return problem;
    }
}
