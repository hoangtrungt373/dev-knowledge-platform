package com.ttg.devknowledgeplatform.devpractice.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.ttg.devknowledgeplatform.devpractice.dto.ProblemSummaryResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.StudyPlanProblemRefResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.StudyPlanResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.StudyPlanSectionResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.StudyPlanSummaryResponse;
import com.ttg.devknowledgeplatform.devpractice.entity.Problem;
import com.ttg.devknowledgeplatform.devpractice.entity.StudyPlan;
import com.ttg.devknowledgeplatform.devpractice.entity.StudyPlanSection;

/**
 * Study plan entity → DTO mapping. Problem rows go through {@link ProblemMapper#toSummaryResponse}
 * ({@code uses}), so a plan lists a problem exactly the way the catalog does — and only its
 * published problems ({@link StudyPlanSection#publishedProblems()}), never a draft, in both the
 * detail rows and the overview card's {@code problems}.
 */
@Mapper(componentModel = "spring", uses = ProblemMapper.class)
public interface StudyPlanMapper {

    @Mapping(target = "sectionCount", expression = "java(plan.getSections().size())")
    @Mapping(target = "problems", expression = "java(publishedProblemRefs(plan))")
    StudyPlanSummaryResponse toSummaryResponse(StudyPlan plan);

    StudyPlanResponse toResponse(StudyPlan plan);

    @Mapping(target = "problems", expression = "java(toProblemSummaries(section.publishedProblems()))")
    StudyPlanSectionResponse toResponse(StudyPlanSection section);

    /** Implemented by MapStruct element by element through {@link ProblemMapper#toSummaryResponse}. */
    List<ProblemSummaryResponse> toProblemSummaries(List<Problem> problems);

    /** The plan's published problems as {@code {id, slug}}, section by section, each in its own order. */
    default List<StudyPlanProblemRefResponse> publishedProblemRefs(StudyPlan plan) {
        return plan.getSections().stream()
                .flatMap(section -> section.publishedProblems().stream())
                .map(problem -> new StudyPlanProblemRefResponse(problem.getId(), problem.getSlug()))
                .toList();
    }
}
