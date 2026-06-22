package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.dto.RagAnswer;
import com.ttg.devknowledgeplatform.ai.dto.RagSource;
import com.ttg.devknowledgeplatform.ai.exception.RagQueryException;
import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import com.ttg.devknowledgeplatform.ai.repository.ContentEmbeddingRepository;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingService;
import com.ttg.devknowledgeplatform.ai.service.RagQueryService;
import com.ttg.devknowledgeplatform.ai.service.RagStreamHandler;
import com.ttg.devknowledgeplatform.common.dto.ConversationTurn;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.StreamingResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;
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
 *   <li>Score each chunk via dot product, filter below {@code similarityThreshold}, sort descending.</li>
 *   <li>Build the LLM message list: system prompt → prior conversation turns → current question.</li>
 *   <li>Call the OpenAI chat model — blocking ({@link #query}) or streaming ({@link #queryStream}).</li>
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
    private static final String GENERIC_ERROR_MESSAGE =
            "Failed to process your question. Please try again later.";

    private final EmbeddingService embeddingService;
    private final ContentEmbeddingRepository contentEmbeddingRepository;
    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final EmbeddingProperties properties;

    /** Pairs a chunk with its pre-computed cosine similarity score. */
    private record ScoredChunk(ContentEmbedding chunk, float score) {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public RagAnswer query(String question, List<ConversationTurn> history) {
        log.info("RAG query: history={} turns", history.size());
        try {
            return doQuery(question, history);
        } catch (RagQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("RAG query failed: {}", e.getMessage(), e);
            throw new RagQueryException(GENERIC_ERROR_MESSAGE, e);
        }
    }

    @Override
    public void queryStream(String question, List<ConversationTurn> history, RagStreamHandler handler) {
        log.info("RAG stream query: history={} turns", history.size());
        try {
            List<ScoredChunk> scored = retrieveAndScore(question);

            if (scored == null) {
                handler.onToken(NO_CONTEXT_ANSWER);
                handler.onComplete();
                return;
            }

            // Send sources before LLM call — client can show citations immediately
            handler.onSources(buildSources(scored));

            streamingChatLanguageModel.generate(
                    buildMessages(question, scored, history),
                    new StreamingResponseHandler<AiMessage>() {
                        @Override
                        public void onNext(String token) {
                            handler.onToken(token);
                        }

                        @Override
                        public void onComplete(Response<AiMessage> response) {
                            log.info("RAG stream completed: {} chunks used", scored.size());
                            handler.onComplete();
                        }

                        @Override
                        public void onError(Throwable error) {
                            log.error("RAG stream LLM error: {}", error.getMessage(), error);
                            handler.onError(new RagQueryException("Streaming failed during generation.", error));
                        }
                    }
            );
        } catch (RagQueryException e) {
            handler.onError(e);
        } catch (Exception e) {
            log.error("RAG stream query failed: {}", e.getMessage(), e);
            handler.onError(new RagQueryException(GENERIC_ERROR_MESSAGE, e));
        }
    }

    // -------------------------------------------------------------------------
    // Private pipeline steps
    // -------------------------------------------------------------------------

    private RagAnswer doQuery(String question, List<ConversationTurn> history) {
        List<ScoredChunk> scored = retrieveAndScore(question);

        if (scored == null) {
            return new RagAnswer(NO_CONTEXT_ANSWER, List.of());
        }

        String answer = generateAnswer(question, scored, history);
        log.info("RAG query completed: {} chunks retrieved, answer length={}", scored.size(), answer.length());
        return new RagAnswer(answer, buildSources(scored));
    }

    /**
     * Embeds the question, fetches top-K chunk IDs from pgvector, loads entities,
     * scores via dot product, filters below the similarity threshold, and sorts descending.
     *
     * @return sorted scored chunks, or {@code null} if no relevant chunks were found
     */
    private List<ScoredChunk> retrieveAndScore(String question) {
        float[] questionEmbedding = embeddingService.embed(question);

        List<Integer> ids = contentEmbeddingRepository.findTopSimilarIds(
                toVectorString(questionEmbedding), properties.getTopK());

        if (ids.isEmpty()) {
            log.warn("No embeddings found in the knowledge base");
            return null;
        }

        List<ContentEmbedding> chunks = contentEmbeddingRepository.findAllByIdWithContentItem(ids);

        List<ScoredChunk> scored = chunks.stream()
                .map(ce -> new ScoredChunk(ce, dotProduct(questionEmbedding, ce.getEmbedding())))
                .filter(sc -> sc.score() >= properties.getSimilarityThreshold())
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .toList();

        if (scored.isEmpty()) {
            log.warn("No chunks passed similarity threshold {}", properties.getSimilarityThreshold());
            return null;
        }

        return scored;
    }

    private List<RagSource> buildSources(List<ScoredChunk> scored) {
        return scored.stream()
                .map(sc -> new RagSource(
                        sc.chunk().getContentItem().getId(),
                        sc.chunk().getSourceType().name(),
                        sc.chunk().getContentItem().getTitle(),
                        sc.chunk().getChunkText(),
                        sc.score()
                ))
                .toList();
    }

    /**
     * Builds the numbered context string {@code [1] chunk1\n\n[2] chunk2...}
     * injected into the system prompt.
     */
    private String buildContext(List<ScoredChunk> scored) {
        return IntStream.range(0, scored.size())
                .mapToObj(i -> "[" + (i + 1) + "] " + scored.get(i).chunk().getChunkText())
                .collect(Collectors.joining("\n\n"));
    }

    private String generateAnswer(String question, List<ScoredChunk> scored, List<ConversationTurn> history) {
        return chatLanguageModel.generate(buildMessages(question, scored, history)).content().text();
    }

    /**
     * Constructs the full message list for the LLM:
     * <ol>
     *   <li>System prompt with numbered context chunks.</li>
     *   <li>Alternating User/Assistant messages from the conversation history (oldest first).</li>
     *   <li>The current user question as the final message.</li>
     * </ol>
     *
     * <p>Prepending history before the current question gives the LLM the full conversational
     * context needed to resolve pronouns and follow-up references ("that", "the one above", etc.).
     */
    private List<ChatMessage> buildMessages(String question, List<ScoredChunk> scored, List<ConversationTurn> history) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(buildSystemPrompt(scored)));
        for (ConversationTurn turn : history) {
            if ("USER".equals(turn.role())) {
                messages.add(UserMessage.from(turn.content()));
            } else {
                messages.add(AiMessage.from(turn.content()));
            }
        }
        messages.add(UserMessage.from(question));
        return messages;
    }

    private String buildSystemPrompt(List<ScoredChunk> scored) {
        return properties.getSystemPrompt() + buildContext(scored);
    }

    /**
     * Computes cosine similarity between two embedding vectors via dot product.
     * Valid because OpenAI's embedding models produce L2-normalized vectors:
     * {@code cosine_similarity(a, b) = dot_product(a, b)}.
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
     * Required by the native {@code CAST(:embedding AS vector)} expression.
     */
    private static String toVectorString(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float f : embedding) {
            joiner.add(String.valueOf(f));
        }
        return joiner.toString();
    }
}
