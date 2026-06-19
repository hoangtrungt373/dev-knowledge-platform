package com.ttg.devknowledgeplatform.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
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
}
