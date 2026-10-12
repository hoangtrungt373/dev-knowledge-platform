package com.ttg.devknowledgeplatform.infra.scheduling;

/**
 * What one {@link AbstractBatchJob#runBatch()} did.
 *
 * @param polled    ids the poll query returned
 * @param processed ids actually acted on ({@code processOne} returned {@code true})
 * @param skipped   ids that needed nothing by the time they were processed — {@code processOne}
 *                  returned {@code false}, or another writer changed the row first (optimistic lock)
 * @param failed    ids whose processing threw; they're retried on the next run
 */
public record BatchRunSummary(int polled, int processed, int skipped, int failed) {

    /** Whether anything happened worth reporting — nothing was acted on or failed means a quiet run. */
    public boolean isNoteworthy() {
        return processed > 0 || failed > 0;
    }
}
