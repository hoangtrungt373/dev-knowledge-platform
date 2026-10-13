package com.ttg.devknowledgeplatform.devpractice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.devpractice.entity.StudyPlan;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.repository.StudyPlanRepository;

/** Plan lookups: overview order comes from the repository, an unknown slug is a 404. */
class StudyPlanServiceImplTest {

    private final StudyPlanRepository repository = mock(StudyPlanRepository.class);
    private final StudyPlanServiceImpl service = new StudyPlanServiceImpl(repository);

    @Test
    void listsPlansInOverviewOrder() {
        List<StudyPlan> plans = List.of(StudyPlan.builder().name("A").build(), StudyPlan.builder().name("B").build());
        when(repository.findAllByOrderByPositionAscNameAsc()).thenReturn(plans);

        assertThat(service.listAll()).isSameAs(plans);
    }

    @Test
    void findsAPlanBySlug() {
        StudyPlan plan = StudyPlan.builder().slug("neetcode-150").build();
        when(repository.findBySlug("neetcode-150")).thenReturn(Optional.of(plan));

        assertThat(service.getBySlug("neetcode-150")).isSameAs(plan);
    }

    @Test
    void anUnknownSlugIsStudyPlanNotFound() {
        when(repository.findBySlug("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBySlug("nope"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(DevPracticeErrorCode.STUDY_PLAN_NOT_FOUND));
    }
}
