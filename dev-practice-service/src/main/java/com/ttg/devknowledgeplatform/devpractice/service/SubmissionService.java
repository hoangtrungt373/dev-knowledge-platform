package com.ttg.devknowledgeplatform.devpractice.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.ttg.devknowledgeplatform.devpractice.entity.Submission;

/**
 * Manages code submissions. <b>Phase 1 scope:</b> {@link #create} only ever persists a submission
 * as {@code PENDING} — no judging pipeline is wired up yet (see {@link Submission}'s Javadoc and
 * this module's own {@code CLAUDE.md}).
 */
public interface SubmissionService {

    Submission create(String userUuid, SubmissionCommands.Create command);

    Submission getSubmission(String userUuid, Integer id);

    Page<Submission> listSubmissions(String userUuid, Integer problemId, Pageable pageable);
}
