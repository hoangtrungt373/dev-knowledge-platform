package com.ttg.devknowledgeplatform.ai.config;

import com.ttg.devknowledgeplatform.common.enums.ChatProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for the set of selectable chat (generation) models.
 *
 * <p>Bound from the {@code app.ai.chat-models} prefix. Each entry in {@link #profiles} is a
 * fully self-contained chat model — its own id, provider, API key, and generation parameters —
 * so adding a new selectable model (or a new tier of an existing provider) is a pure
 * configuration change; no code change or redeploy is required.
 *
 * <p>{@code ChatRequest.chatModel()} carries the id of the profile a client wants to use;
 * {@code null} falls back to {@link #defaultModel}. An id that does not match any configured
 * profile is rejected with {@code CommonErrorCode.AI_MODEL_UNSUPPORTED} — the profile list doubles
 * as the allow-list, so clients can never invoke a model this application hasn't budgeted for.
 */
@ConfigurationProperties(prefix = "app.ai.chat-models")
@Validated
@Getter
@Setter
public class ChatModelsConfig {

    /** Model id used when a request does not specify {@code chatModel}. Must match a {@link #profiles} entry. */
    @NotBlank
    private String defaultModel = "gpt-5.4-mini";

    /** The full set of selectable chat models. */
    @NotEmpty
    @Valid
    private List<ChatModelProfile> profiles = new ArrayList<>();

    /**
     * A single selectable chat model: which provider serves it, its own API key, and its own
     * generation parameters. Kept fully self-contained — rather than sharing one key per
     * provider — so a profile never needs to cross-reference another config class to be usable.
     */
    @Getter
    @Setter
    public static class ChatModelProfile {

        /** Id clients pass as {@code ChatRequest.chatModel} (e.g. {@code "gpt-5.4-mini"}, {@code "claude-sonnet-5"}). */
        @NotBlank
        private String id;

        /** Which LangChain4j builder family this profile is wired through. */
        @NotNull
        private ChatProvider provider;

        /** API key for this profile's provider. */
        @NotBlank
        private String apiKey;

        @Positive
        private int maxTokens = 1024;

        private double temperature = 0.7;

        /**
         * Maximum number of retries for failed LLM API calls.
         * Not applied to streaming calls — retrying mid-stream is not meaningful
         * because partial token output cannot be rolled back.
         */
        @Positive
        private int maxRetries = 3;
    }
}
