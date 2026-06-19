package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.dto.RagAnswer;
import com.ttg.devknowledgeplatform.ai.dto.RagSource;
import com.ttg.devknowledgeplatform.ai.exception.RagQueryException;
import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import com.ttg.devknowledgeplatform.ai.repository.ContentEmbeddingRepository;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingService;
import com.ttg.devknowledgeplatform.ai.service.RagQueryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.IntStream;

/**
 * Default {@link RagQueryService} implementation.
 *
 * <p>Pipeline per query:
 * <ol>
 *   <li>Embed the question via {@link EmbeddingService} (OpenAI text-embedding-3-small).</li>
 *   <li>Run a native pgvector cosine-distance query to fetch the top-K chunk IDs.</li>
 *   <li>Load those chunks with their parent {@code ContentItem} eagerly (JOIN FETCH)
 *       so no lazy-init issues arise after the repository transaction closes.</li>
 *   <li>Score each chunk via dot product, filter below {@code similarityThreshold}, sort descending, call the OpenAI chat model.</li>
 * </ol>
 *
 * <p>No {@code @Transactional} is placed on this class intentionally: the two repository calls each
 * run in their own short-lived read-only transaction, and the DB connection is fully released before
 * the LLM HTTP call begins.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagQueryServiceImpl implements RagQueryService {

    private static final String NO_CONTEXT_ANSWER =
            "I don't have relevant information in my knowledge base to answer this question.";

    private final EmbeddingService embeddingService;
    private final ContentEmbeddingRepository contentEmbeddingRepository;
    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingProperties properties;

    @Override
    public RagAnswer query(String question) {
        log.info("RAG query: {}", question);
        try {
            return doQuery(question);
        } catch (RagQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("RAG query failed: {}", e.getMessage(), e);
            throw new RagQueryException("Failed to process your question. Please try again later.", e);
        }
    }

    private RagAnswer doQuery(String question) {
        // 1. Embed the question
        float[] questionEmbedding = embeddingService.embed(question);

        // 2. Find top-K IDs by cosine distance (native pgvector query)
        List<Integer> ids = contentEmbeddingRepository.findTopSimilarIds(
                toVectorString(questionEmbedding), properties.getTopK());

        if (ids.isEmpty()) {
            log.warn("No embeddings found in the knowledge base");
            return new RagAnswer(NO_CONTEXT_ANSWER, List.of());
        }

        // 3. Load entities with content item eagerly (avoids LazyInitializationException)
        List<ContentEmbedding> chunks = contentEmbeddingRepository.findAllByIdWithContentItem(ids);

        // 4. Score once, filter by threshold, sort descending, build sources
        record ScoredChunk(ContentEmbedding chunk, float score) {}
        List<ScoredChunk> scored = chunks.stream()
                .map(ce -> new ScoredChunk(ce, dotProduct(questionEmbedding, ce.getEmbedding())))
                .filter(sc -> sc.score() >= properties.getSimilarityThreshold())
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .toList();

        if (scored.isEmpty()) {
            log.warn("No chunks passed similarity threshold {}", properties.getSimilarityThreshold());
            return new RagAnswer(NO_CONTEXT_ANSWER, List.of());
        }

        List<RagSource> sources = scored.stream()
                .map(sc -> new RagSource(
                        sc.chunk().getContentItem().getId(),
                        sc.chunk().getSourceType().name(),
                        sc.chunk().getContentItem().getTitle(),
                        sc.chunk().getChunkText(),
                        sc.score()
                ))
                .toList();

        // 5. Call LLM with retrieved context (preserve sorted order)
        List<ContentEmbedding> sortedChunks = scored.stream().map(ScoredChunk::chunk).toList();
        String answer = generateAnswer(question, sortedChunks);

        log.info("RAG query completed: {} chunks retrieved, answer length={}", scored.size(), answer.length());
        return new RagAnswer(answer, sources);
    }

    /**
     * Builds the system prompt from the retrieved chunks and calls the chat model.
     * Chunks are numbered [1]…[N] so the LLM can reference them in its answer.
     */
    private String generateAnswer(String question, List<ContentEmbedding> chunks) {
        String context = IntStream.range(0, chunks.size())
                .mapToObj(i -> "[" + (i + 1) + "] " + chunks.get(i).getChunkText())
                .collect(java.util.stream.Collectors.joining("\n\n"));

        String systemPrompt = properties.getSystemPrompt() + context;

        Response<AiMessage> response = chatLanguageModel.generate(
                List.of(
                        SystemMessage.from(systemPrompt),
                        UserMessage.from(question)
                )
        );
        return response.content().text();
    }

    /**
     * Computes cosine similarity between two embedding vectors via dot product.
     * This is valid because OpenAI's embedding models produce L2-normalized vectors,
     * meaning {@code cosine_similarity(a, b) = dot_product(a, b)}.
     *
     * @return similarity in [0, 1]; 1.0 = identical direction, 0.0 = orthogonal
     */
    private static float dotProduct(float[] a, float[] b) {
        float sum = 0f;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /**
     * Formats a float array as the pgvector text literal {@code [x,y,z,...]}.
     * This is the format expected by the native {@code CAST(:embedding AS vector)} expression.
     */
    private static String toVectorString(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float f : embedding) {
            joiner.add(String.valueOf(f));
        }
        return joiner.toString();
    }
}
