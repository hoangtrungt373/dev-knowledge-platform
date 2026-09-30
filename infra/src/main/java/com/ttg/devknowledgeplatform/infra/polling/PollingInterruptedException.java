package com.ttg.devknowledgeplatform.infra.polling;

/**
 * Thrown by {@link PollingTemplate#poll} when the polling thread was interrupted — during a wait
 * between attempts, or while the probe itself was running. The thread's interrupt flag is left set,
 * so code further up the stack can still observe the interruption.
 */
public class PollingInterruptedException extends RuntimeException {

    /**
     * @param policyName the {@link PollingPolicy#name()} being polled
     * @param cause      whatever surfaced from the interrupted wait or probe
     */
    public PollingInterruptedException(String policyName, Throwable cause) {
        super("Interrupted while polling '" + policyName + "'", cause);
    }
}
