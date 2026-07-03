package com.ttg.devknowledgeplatform.ai.config;

import com.ttg.devknowledgeplatform.common.enums.ChatProvider;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Spring configuration that wires the LangChain4j LLM beans.
 *
 * <p>All {@code @ConfigurationProperties} classes ({@link ModelConfig}, {@link ChatModelsConfig},
 * {@link IndexingConfig}, {@link RetrievalConfig}, {@link GuardConfig}, {@link LabelsConfig},
 * {@link OkHttpProperties}) are auto-registered by {@code @ConfigurationPropertiesScan} on the
 * main application class — no explicit {@code @EnableConfigurationProperties} is needed here.
 *
 * <p>Chat models are built one-per-{@link ChatModelsConfig.ChatModelProfile} rather than as a
 * single fixed bean, so a request can select any configured model at runtime — see
 * {@code ChatModelResolver}, which looks up a bean from the maps produced here by model id.
 * Embedding stays a single fixed model (see {@code OpenAiEmbeddingServiceImpl}) since only
 * OpenAI offers an embedding product today.
 */
@Configuration
@EnableScheduling
public class AiServiceConfig {

    /**
     * One blocking {@link ChatLanguageModel} per configured chat profile, keyed by
     * {@link ChatModelsConfig.ChatModelProfile#getId()}.
     *
     * @param chatModels chat model profiles (id, provider, api key, generation parameters)
     * @param okHttp     HTTP client config (overall timeout per API call, applies to every provider)
     */
    @Bean
    public Map<String, ChatLanguageModel> chatLanguageModels(ChatModelsConfig chatModels, OkHttpProperties okHttp) {
        Map<String, ChatLanguageModel> models = new LinkedHashMap<>();
        for (ChatModelsConfig.ChatModelProfile profile : chatModels.getProfiles()) {
            models.put(profile.getId(), buildBlocking(profile, okHttp));
        }
        return models;
    }

    /**
     * Streaming variant of {@link #chatLanguageModels} — one per configured chat profile, keyed
     * the same way. Fires token-by-token callbacks instead of buffering the full response.
     * Used by {@code RagQueryService#queryStream}.
     *
     * <p>Note: {@code maxRetries} is intentionally omitted — retrying mid-stream is not
     * meaningful because partial token output cannot be rolled back.
     *
     * @param chatModels chat model profiles (id, provider, api key, generation parameters)
     * @param okHttp     HTTP client config (overall timeout per API call, applies to every provider)
     */
    @Bean
    public Map<String, StreamingChatLanguageModel> streamingChatLanguageModels(ChatModelsConfig chatModels, OkHttpProperties okHttp) {
        Map<String, StreamingChatLanguageModel> models = new LinkedHashMap<>();
        for (ChatModelsConfig.ChatModelProfile profile : chatModels.getProfiles()) {
            models.put(profile.getId(), buildStreaming(profile, okHttp));
        }
        return models;
    }

    private ChatLanguageModel buildBlocking(ChatModelsConfig.ChatModelProfile profile, OkHttpProperties okHttp) {
        if (profile.getProvider() == ChatProvider.ANTHROPIC) {
            return AnthropicChatModel.builder()
                    .apiKey(profile.getApiKey())
                    .modelName(profile.getId())
                    .maxTokens(profile.getMaxTokens())
                    .temperature(profile.getTemperature())
                    .maxRetries(profile.getMaxRetries())
                    .timeout(okHttp.getTimeout())
                    .build();
        }
        return OpenAiChatModel.builder()
                .apiKey(profile.getApiKey())
                .modelName(profile.getId())
                .maxTokens(profile.getMaxTokens())
                .temperature(profile.getTemperature())
                .maxRetries(profile.getMaxRetries())
                .timeout(okHttp.getTimeout())
                .build();
    }

    private StreamingChatLanguageModel buildStreaming(ChatModelsConfig.ChatModelProfile profile, OkHttpProperties okHttp) {
        if (profile.getProvider() == ChatProvider.ANTHROPIC) {
            return AnthropicStreamingChatModel.builder()
                    .apiKey(profile.getApiKey())
                    .modelName(profile.getId())
                    .maxTokens(profile.getMaxTokens())
                    .temperature(profile.getTemperature())
                    .timeout(okHttp.getTimeout())
                    .build();
        }
        return OpenAiStreamingChatModel.builder()
                .apiKey(profile.getApiKey())
                .modelName(profile.getId())
                .maxTokens(profile.getMaxTokens())
                .temperature(profile.getTemperature())
                .timeout(okHttp.getTimeout())
                .build();
    }
}
