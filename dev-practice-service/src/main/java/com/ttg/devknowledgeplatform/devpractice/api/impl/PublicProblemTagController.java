package com.ttg.devknowledgeplatform.devpractice.api.impl;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.ttg.devknowledgeplatform.devpractice.api.PublicProblemTagApi;
import com.ttg.devknowledgeplatform.devpractice.dto.ProblemTagSummaryResponse;
import com.ttg.devknowledgeplatform.devpractice.mapper.ProblemMapper;
import com.ttg.devknowledgeplatform.devpractice.service.ProblemTagService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PublicProblemTagController implements PublicProblemTagApi {

    private final ProblemTagService problemTagService;
    private final ProblemMapper problemMapper;

    @Override
    public ResponseEntity<List<ProblemTagSummaryResponse>> list() {
        return ResponseEntity.ok(problemTagService.listAll().stream().map(problemMapper::toSummary).toList());
    }
}
