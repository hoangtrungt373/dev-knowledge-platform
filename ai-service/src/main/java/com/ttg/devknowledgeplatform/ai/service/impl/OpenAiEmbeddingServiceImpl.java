package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class OpenAiEmbeddingServiceImpl implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public OpenAiEmbeddingServiceImpl(EmbeddingProperties properties) {
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .dimensions(properties.getDimensions())
                .maxRetries(properties.getMaxRetries())
                .build();
        log.info("EmbeddingService initialised: model={} dimensions={}",
                properties.getModel(), properties.getDimensions());
    }

    @Override
    public float[] embed(String text) {
        return embeddingModel.embed(text).content().vector();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<TextSegment> segments = texts.stream()
                .map(TextSegment::from)
                .toList();
        return embeddingModel.embedAll(segments).content().stream()
                .map(Embedding::vector)
                .toList();
    }
}
