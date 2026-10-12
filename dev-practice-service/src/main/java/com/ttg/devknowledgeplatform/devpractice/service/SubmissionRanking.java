package com.ttg.devknowledgeplatform.devpractice.service;

/**
 * How an accepted submission compares with other learners' — LeetCode's "Beats 87%". Computed on
 * read, never stored, so it moves as more people solve the problem.
 *
 * @param runtimeBeatsPercent share of other users whose best runtime is strictly slower, 0–100 (one
 *                            decimal); {@code null} when there is nobody to compare with
 * @param memoryBeatsPercent  same for peak memory (strictly more memory)
 * @param comparedWith        how many other users' best runs it was compared with — each user once,
 *                            same problem, same language, same test data
 */
public record SubmissionRanking(Double runtimeBeatsPercent, Double memoryBeatsPercent, long comparedWith) {
}
