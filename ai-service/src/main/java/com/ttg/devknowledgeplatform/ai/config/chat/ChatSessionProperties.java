package com.ttg.devknowledgeplatform.ai.config.chat;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties governing chat session lifecycle and rolling summarisation triggers.
 *
 * <p>Bound from the {@code app.chat.session} prefix. Override defaults via environment variables:
 * <ul>
 *   <li>{@code CHAT_SESSION_TTL_HOURS} — inactivity window before a session is considered expired (default: 24)</li>
 *   <li>{@code CHAT_SUMMARY_THRESHOLD_PAIRS} — Q&amp;A pair count that triggers the first summarisation (default: 12)</li>
 *   <li>{@code CHAT_SUMMARY_TRIGGER_INTERVAL_PAIRS} — re-summarise every N new pairs beyond the threshold (default: 4)</li>
 *   <li>{@code CHAT_SUMMARY_RECENT_WINDOW_PAIRS} — verbatim pairs kept outside the summary (default: 5)</li>
 * </ul>
 *
 * <p>The constraint {@code summaryThresholdPairs > summaryRecentWindowPairs} must hold so that the
 * first compression covers at least one pair. Validation of this cross-field invariant is left to
 * startup logs — violating it produces no answer degradation, only a no-op summarisation call.
 */
@ConfigurationProperties(prefix = "app.chat.session")
@Validated
@Getter
@Setter
public class ChatSessionProperties {

    /** Hours of inactivity after which a session's message history is cleared on next access. */
    @Positive
    private int ttlHours = 24;

    /**
     * Q&amp;A pair count at which the first rolling summary is generated.
     *
     * <p>Must satisfy {@code summaryThresholdPairs > summaryRecentWindowPairs} so that the first
     * compression covers {@code threshold - recentWindow} pairs — enough to justify the LLM call.
     */
    @Positive
    private int summaryThresholdPairs = 12;

    /** A new summary is generated every N Q&amp;A pairs added beyond {@link #summaryThresholdPairs}. */
    @Positive
    private int summaryTriggerIntervalPairs = 4;

    /**
     * The most recent N Q&amp;A pairs are always kept as verbatim messages and excluded from
     * compression, ensuring the RAG pipeline has full near-context without parsing the summary.
     */
    @Positive
    private int summaryRecentWindowPairs = 5;
}
