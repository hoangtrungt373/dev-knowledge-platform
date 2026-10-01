package com.ttg.devknowledgeplatform.devpractice.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.ttg.devknowledgeplatform.devpractice.dto.ProblemTagSummaryResponse;

/**
 * Public, unauthenticated tag list — what a problem browser shows as its topic filter. Tags carry
 * no private data, so every tag is listed regardless of whether a published problem uses it yet.
 */
@RequestMapping("/api/v1/public/problem-tags")
public interface PublicProblemTagApi {

    /**
     * @return {@code 200} with every tag, sorted by name
     */
    @GetMapping
    ResponseEntity<List<ProblemTagSummaryResponse>> list();
}
