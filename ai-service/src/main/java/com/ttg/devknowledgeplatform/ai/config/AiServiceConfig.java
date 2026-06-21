package com.ttg.devknowledgeplatform.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class AiServiceConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(EmbeddingProperties properties) {
        return OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getChatModel())
                .maxTokens(properties.getMaxTokens())
                .temperature(properties.getTemperature())
                .maxRetries(properties.getMaxRetries())
                .build();
    }

    /**
     * Streaming variant of the chat model — fires token-by-token callbacks instead of
     * buffering the full response. Used by {@code RagQueryService#queryStream}.
     *
     * <p>Note: {@code maxRetries} is intentionally omitted — retrying mid-stream is not
     * meaningful because partial token output cannot be rolled back.
     */
    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(EmbeddingProperties properties) {
        return OpenAiStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getChatModel())
                .maxTokens(properties.getMaxTokens())
                .temperature(properties.getTemperature())
                .build();
    }
}
