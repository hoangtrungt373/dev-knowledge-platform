package com.ttg.devknowledgeplatform.devpractice.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;

/** The aggregate's own rules: positions follow insertion order, and drafts never reach learners. */
class StudyPlanTest {

    @Test
    void sectionsAndProblemsAreNumberedInTheOrderTheyAreAdded() {
        StudyPlan plan = StudyPlan.builder().name("Plan").build();
        StudyPlanSection first = plan.addSection("Arrays & Hashing", null);
        StudyPlanSection second = plan.addSection("Stack", "LIFO");

        StudyPlanProblem a = first.addProblem(problem(1, ContentStatus.PUBLISHED));
        StudyPlanProblem b = first.addProblem(problem(2, ContentStatus.PUBLISHED));

        assertThat(first.getPosition()).isZero();
        assertThat(second.getPosition()).isEqualTo(1);
        assertThat(second.getStudyPlan()).isSameAs(plan);
        assertThat(a.getPosition()).isZero();
        assertThat(b.getPosition()).isEqualTo(1);
        assertThat(b.getSection()).isSameAs(first);
    }

    @Test
    void publishedProblemsKeepsPlanOrderAndLeavesOutDrafts() {
        StudyPlanSection section = StudyPlan.builder().name("Plan").build().addSection("Stack", null);
        Problem published1 = problem(1, ContentStatus.PUBLISHED);
        Problem draft = problem(2, ContentStatus.DRAFT);
        Problem published3 = problem(3, ContentStatus.PUBLISHED);
        section.addProblem(published1);
        section.addProblem(draft);
        section.addProblem(published3);

        assertThat(section.publishedProblems()).containsExactly(published1, published3);
    }

    private static Problem problem(int id, ContentStatus status) {
        Problem problem = Problem.builder().status(status).build();
        problem.setId(id);
        return problem;
    }
}
