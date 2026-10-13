package com.ttg.devknowledgeplatform.devpractice.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.exception.ApiException;
import com.ttg.devknowledgeplatform.devpractice.entity.StudyPlan;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.repository.StudyPlanRepository;
import com.ttg.devknowledgeplatform.devpractice.service.StudyPlanService;

import lombok.RequiredArgsConstructor;

/** Read-only study-plan lookups; filtering out unpublished problems is the section's own job. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyPlanServiceImpl implements StudyPlanService {

    private final StudyPlanRepository studyPlanRepository;

    @Override
    public List<StudyPlan> listAll() {
        return studyPlanRepository.findAllByOrderByPositionAscNameAsc();
    }

    @Override
    public StudyPlan getBySlug(String slug) {
        return studyPlanRepository.findBySlug(slug)
                .orElseThrow(() -> new ApiException(DevPracticeErrorCode.STUDY_PLAN_NOT_FOUND));
    }
}
