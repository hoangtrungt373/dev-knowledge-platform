package com.ttg.devknowledgeplatform.devpractice.dto;

/**
 * Just enough of a plan's problem for its overview card: the id (checked against the learner's
 * progress) and the slug (to open the workspace — e.g. "Continue" jumps to the first unsolved one).
 */
public record StudyPlanProblemRefResponse(Integer id, String slug) {
}
