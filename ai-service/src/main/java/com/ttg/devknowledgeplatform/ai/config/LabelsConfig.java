package com.ttg.devknowledgeplatform.ai.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for prompt assembly label strings injected into LLM messages.
 *
 * <p>Bound from the {@code app.ai.labels} prefix. These strings act as structural markers
 * within assembled prompts and must not be blank. Operators may customise the wording via
 * environment variable overrides without a code change.
 */
@ConfigurationProperties(prefix = "app.ai.labels")
@Validated
@Getter
@Setter
public class LabelsConfig {

    /** Label prepended to the rolling summary in the contextualization rewrite prompt. */
    @NotBlank
    private String contextSummaryLabel = "Summary of earlier conversation:\n";

    /** Label prepended to the current user question in the contextualization prompt. */
    @NotBlank
    private String contextFollowUpLabel = "\nFollow-up: ";

    /** Label used as the user message injecting the rolling summary into the LLM message list. */
    @NotBlank
    private String historySummaryLabel = "Earlier conversation summary:\n";

    /** Synthetic AI acknowledgement closing the injected summary User/Assistant exchange. */
    @NotBlank
    private String historySummaryAck = "Understood. I will keep this context in mind while answering.";

    /** Label prepended to the previous summary in the compression prompt. */
    @NotBlank
    private String compressionPreviousSummaryLabel = "\n\nPrevious summary (extend this, do not repeat it verbatim):\n";

    /** Label separating the previous summary from the new turns to compress. */
    @NotBlank
    private String compressionTurnsLabel = "\n\nConversation turns to compress:\n";
}
