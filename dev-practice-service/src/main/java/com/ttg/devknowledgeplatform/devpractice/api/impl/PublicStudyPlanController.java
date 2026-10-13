package com.ttg.devknowledgeplatform.devpractice.api.impl;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.devpractice.api.PublicStudyPlanApi;
import com.ttg.devknowledgeplatform.devpractice.dto.StudyPlanResponse;
import com.ttg.devknowledgeplatform.devpractice.dto.StudyPlanSummaryResponse;
import com.ttg.devknowledgeplatform.devpractice.mapper.StudyPlanMapper;
import com.ttg.devknowledgeplatform.devpractice.service.StudyPlanService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PublicStudyPlanController implements PublicStudyPlanApi {

    private final StudyPlanService studyPlanService;
    private final StudyPlanMapper studyPlanMapper;

    @Override
    public ResponseEntity<List<StudyPlanSummaryResponse>> list() {
        return ResponseEntity.ok(studyPlanService.listAll().stream().map(studyPlanMapper::toSummaryResponse).toList());
    }

    @Override
    public ResponseEntity<StudyPlanResponse> getBySlug(String slug) {
        return ResponseEntity.ok(studyPlanMapper.toResponse(studyPlanService.getBySlug(slug)));
    }
}
