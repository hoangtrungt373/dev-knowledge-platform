package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.config.ModelConfig;
import com.ttg.devknowledgeplatform.ai.dto.EmbedResult;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class OpenAiEmbeddingServiceImpl implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public OpenAiEmbeddingServiceImpl(ModelConfig model) {
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(model.getApiKey())
                .modelName(model.getModel())
                .dimensions(model.getDimensions())
                .maxRetries(model.getMaxRetries())
                .build();
        log.info("EmbeddingService initialised: model={} dimensions={}",
                model.getModel(), model.getDimensions());
    }

    @Override
    public EmbedResult embed(String text) {
        Response<Embedding> response = embeddingModel.embed(text);
        int tokenCount = (response.tokenUsage() != null && response.tokenUsage().inputTokenCount() != null)
                ? response.tokenUsage().inputTokenCount()
                : 0;
        return new EmbedResult(response.content().vector(), tokenCount);
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
