package com.ttg.devknowledgeplatform.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring configuration that wires the LangChain4j LLM beans.
 *
 * <p>All {@code @ConfigurationProperties} classes ({@link ModelConfig}, {@link IndexingConfig},
 * {@link RetrievalConfig}, {@link GuardConfig}, {@link LabelsConfig}, {@link OkHttpProperties})
 * are auto-registered by {@code @ConfigurationPropertiesScan} on the main application class —
 * no explicit {@code @EnableConfigurationProperties} is needed here.
 */
@Configuration
@EnableScheduling
public class AiServiceConfig {

    /**
     * Blocking chat model for synchronous RAG queries.
     *
     * @param model  LLM model config (api key, model name, tokens, temperature, retries)
     * @param okHttp HTTP client config (overall timeout per API call)
     */
    @Bean
    public ChatLanguageModel chatLanguageModel(ModelConfig model, OkHttpProperties okHttp) {
        return OpenAiChatModel.builder()
                .apiKey(model.getApiKey())
                .modelName(model.getChatModel())
                .maxTokens(model.getMaxTokens())
                .temperature(model.getTemperature())
                .maxRetries(model.getMaxRetries())
                .timeout(okHttp.getTimeout())
                .build();
    }

    /**
     * Streaming variant of the chat model — fires token-by-token callbacks instead of
     * buffering the full response. Used by {@code RagQueryService#queryStream}.
     *
     * <p>Note: {@code maxRetries} is intentionally omitted — retrying mid-stream is not
     * meaningful because partial token output cannot be rolled back.
     *
     * @param model  LLM model config
     * @param okHttp HTTP client config (overall timeout per API call)
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(ModelConfig model, OkHttpProperties okHttp) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(model.getApiKey())
                .modelName(model.getChatModel())
                .maxTokens(model.getMaxTokens())
                .temperature(model.getTemperature())
                .timeout(okHttp.getTimeout())
                .build();
    }
}
