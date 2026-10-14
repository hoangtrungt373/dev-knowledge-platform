package com.ttg.devknowledgeplatform.devpractice.service.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.StudyPlan;
import com.ttg.devknowledgeplatform.devpractice.entity.StudyPlanProblem;
import com.ttg.devknowledgeplatform.devpractice.entity.StudyPlanSection;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.StudyPlanRepository;
import com.ttg.devknowledgeplatform.infra.service.SlugService;
import com.ttg.devknowledgeplatform.infra.service.impl.SlugServiceImpl;

/**
 * Runs the real {@code data/study-plans/*.yml} through {@link StudyPlanSeeder}, against only the
 * problem slugs the real {@code data/problems/*.md} files produce — so a plan referencing a problem
 * that isn't seeded fails here, not at startup.
 */
class StudyPlanSeederTest {

    private static final Pattern TITLE = Pattern.compile("(?m)^title:\\s*(.+?)\\s*$");

    private final StudyPlanRepository studyPlanRepository = mock(StudyPlanRepository.class);
    private final ProblemRepository problemRepository = mock(ProblemRepository.class);
    // The real slug rules: plan and problem slugs must come out exactly as they will at startup.
    private final SlugService slugService = new SlugServiceImpl();
    private final StudyPlanSeeder seeder = new StudyPlanSeeder(studyPlanRepository, problemRepository, slugService);

    @BeforeEach
    void setUp() throws IOException {
        List<String> seededProblemSlugs = seededProblemTitles().stream().map(slugService::toSlug).toList();
        when(problemRepository.findBySlug(anyString())).thenAnswer(inv -> {
            String slug = inv.getArgument(0);
            if (!seededProblemSlugs.contains(slug)) {
                return Optional.empty();
            }
            Problem problem = Problem.builder().slug(slug).build();
            problem.setId(slug.hashCode());
            return Optional.of(problem);
        });
        when(studyPlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void seedsNeetCode150WithOrderedSectionsAndProblems() {
        assertThat(seeder.seed()).isEqualTo(1);

        StudyPlan plan = savedPlan();
        assertThat(plan.getName()).isEqualTo("NeetCode 150");
        assertThat(plan.getSlug()).isEqualTo("neetcode-150");
        assertThat(plan.getPosition()).isEqualTo(1);
        assertThat(plan.getDescription()).isNotBlank();

        assertThat(plan.getSections()).extracting(StudyPlanSection::getTitle).containsExactly("Arrays & Hashing", "Stack");
        assertThat(plan.getSections()).extracting(StudyPlanSection::getPosition).containsExactly(0, 1);
        assertThat(plan.getSections()).allSatisfy(section -> assertThat(section.getStudyPlan()).isSameAs(plan));

        assertThat(problemSlugs(plan.getSections().get(0))).containsExactly("contains-duplicate", "valid-anagram");
        assertThat(problemSlugs(plan.getSections().get(1))).containsExactly("evaluate-reverse-polish-notation");
        assertThat(plan.getSections().get(0).getProblems())
                .allSatisfy(entry -> assertThat(entry.getSection()).isSameAs(plan.getSections().get(0)))
                .extracting(StudyPlanProblem::getPosition).containsExactly(0, 1);
    }

    @Test
    void skipsAPlanThatAlreadyExists() {
        when(studyPlanRepository.existsBySlug("neetcode-150")).thenReturn(true);

        assertThat(seeder.seed()).isZero();

        verify(studyPlanRepository, never()).save(any());
    }

    @Test
    void anUnknownProblemSlugFailsNamingTheFileAndTheSlug() {
        assertThatThrownBy(() -> seeder.seed("classpath*:study-plans-invalid/unknown-problem.yml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown-problem.yml")
                .hasMessageContaining("no-such-problem");
        verify(studyPlanRepository, never()).save(any());
    }

    @Test
    void aProblemListedTwiceInOnePlanFails() {
        assertThatThrownBy(() -> seeder.seed("classpath*:study-plans-invalid/duplicate-problem.yml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate-problem.yml")
                .hasMessageContaining("contains-duplicate");
    }

    @Test
    void aPlanWithoutSectionsFails() {
        assertThatThrownBy(() -> seeder.seed("classpath*:study-plans-invalid/missing-sections.yml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-sections.yml")
                .hasMessageContaining("'sections'");
    }

    private StudyPlan savedPlan() {
        ArgumentCaptor<StudyPlan> captor = ArgumentCaptor.forClass(StudyPlan.class);
        verify(studyPlanRepository).save(captor.capture());
        return captor.getValue();
    }

    private static List<String> problemSlugs(StudyPlanSection section) {
        return section.getProblems().stream().map(entry -> entry.getProblem().getSlug()).toList();
    }

    /** The {@code title:} of every problem seed file — what ProblemSeeder derives each slug from. */
    private static List<String> seededProblemTitles() throws IOException {
        List<String> titles = new ArrayList<>();
        for (Resource file : new PathMatchingResourcePatternResolver().getResources("classpath*:data/problems/*.md")) {
            try (InputStream in = file.getInputStream()) {
                Matcher matcher = TITLE.matcher(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                if (matcher.find()) {
                    titles.add(matcher.group(1));
                }
            }
        }
        assertThat(titles).as("problem seed files").isNotEmpty();
        return titles;
    }
}
