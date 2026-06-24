package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.dto.RagAnswer;
import com.ttg.devknowledgeplatform.ai.dto.RagSource;
import com.ttg.devknowledgeplatform.ai.entity.ContentEmbedding;
import com.ttg.devknowledgeplatform.ai.exception.RagQueryException;
import com.ttg.devknowledgeplatform.ai.filter.RagFilter;
import com.ttg.devknowledgeplatform.ai.filter.RagFilterStrategy;
import com.ttg.devknowledgeplatform.ai.repository.ContentEmbeddingRepository;
import com.ttg.devknowledgeplatform.ai.service.EmbeddingService;
import com.ttg.devknowledgeplatform.ai.service.RagQueryService;
import com.ttg.devknowledgeplatform.ai.service.RagStreamHandler;
import com.ttg.devknowledgeplatform.common.dto.ConversationTurn;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Default {@link RagQueryService} implementation.
 *
 * <p>Pipeline per query:
 * <ol>
 *   <li>Contextualise the question against conversation history (optional LLM rewrite).</li>
 *   <li>Embed the resulting query string via {@link EmbeddingService}.</li>
 *   <li>Fetch candidate chunk IDs from pgvector using cosine distance, oversampling when a
 *       {@link RagFilter} is active to compensate for post-filter losses.</li>
 *   <li>Load chunks with their parent {@code ContentItem} eagerly (JOIN FETCH).</li>
 *   <li>Apply composed {@link RagFilterStrategy} predicates to the candidate set.</li>
 *   <li>Score via dot product, filter below {@code similarityThreshold}, sort descending,
 *       then cut back to {@code topK}.</li>
 *   <li>Build the LLM message list and generate — blocking or streaming.</li>
 * </ol>
 *
 * <p>No {@code @Transactional} is placed on this class intentionally: the two repository calls
 * each run in their own short-lived read-only transaction, and the DB connection is fully
 * released before the LLM HTTP call begins.
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
    /** All {@code @Component} implementations of {@link RagFilterStrategy} collected by Spring. */
    private final List<RagFilterStrategy> filterStrategies;

    /** Pairs a chunk with its pre-computed cosine similarity score. */
    private record ScoredChunk(ContentEmbedding chunk, float score) {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public RagAnswer query(String question, List<ConversationTurn> history, RagFilter filter) {
        log.info("RAG query: history={} turns, filter={}",
                history.size(), filter.isEmpty() ? "none" : filter);
        try {
            return doQuery(question, history, filter);
        } catch (RagQueryException e) {
            throw e;
        } catch (Exception e) {
            log.error("RAG query failed: {}", e.getMessage(), e);
            throw new RagQueryException(GENERIC_ERROR_MESSAGE, e);
        }
    }

    @Override
    public void queryStream(String question, List<ConversationTurn> history,
                            RagFilter filter, RagStreamHandler handler) {
        log.info("RAG stream query: history={} turns, filter={}",
                history.size(), filter.isEmpty() ? "none" : filter);
        try {
            String retrievalQuery = contextualizeQuestion(question, history);
            List<ScoredChunk> scored = retrieveAndScore(retrievalQuery, filter);

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

    private RagAnswer doQuery(String question, List<ConversationTurn> history, RagFilter filter) {
        String retrievalQuery = contextualizeQuestion(question, history);
        List<ScoredChunk> scored = retrieveAndScore(retrievalQuery, filter);

        if (scored == null) {
            return new RagAnswer(NO_CONTEXT_ANSWER, List.of());
        }

        String answer = generateAnswer(question, scored, history);
        log.info("RAG query completed: {} chunks retrieved, answer length={}", scored.size(), answer.length());
        return new RagAnswer(answer, buildSources(scored));
    }

    /**
     * Embeds the question, fetches candidate chunk IDs from pgvector, applies {@link RagFilterStrategy}
     * predicates, scores via dot product, filters below the similarity threshold, and returns the
     * top-K results sorted by descending similarity.
     *
     * <p>When {@code filter} is non-empty the initial pgvector query fetches
     * {@code topK × oversampleFactor} candidates so that the post-filter candidate pool remains
     * large enough to yield {@code topK} results after filtering. Without oversampling, an
     * aggressive filter could reduce the pool to zero even when relevant chunks exist further
     * down the HNSW graph.
     *
     * @return sorted scored chunks, or {@code null} if no relevant chunks survived filter + threshold
     */
    private List<ScoredChunk> retrieveAndScore(String question, RagFilter filter) {
        float[] questionEmbedding = embeddingService.embed(question);

        int candidateLimit = filter.isEmpty()
                ? properties.getTopK()
                : properties.getTopK() * properties.getOversampleFactor();

        List<Integer> ids = contentEmbeddingRepository.findTopSimilarIds(
                toVectorString(questionEmbedding), candidateLimit);

        if (ids.isEmpty()) {
            log.warn("No embeddings found in the knowledge base");
            return null;
        }

        List<ContentEmbedding> chunks = contentEmbeddingRepository.findAllByIdWithContentItem(ids);

        // Compose all applicable strategy predicates with AND semantics
        Predicate<ContentEmbedding> compositePredicate = filterStrategies.stream()
                .filter(s -> s.isApplicable(filter))
                .map(s -> s.predicate(filter))
                .reduce(Predicate::and)
                .orElse(ce -> true);

        List<ScoredChunk> scored = chunks.stream()
                .filter(compositePredicate)
                .map(ce -> new ScoredChunk(ce, dotProduct(questionEmbedding, ce.getEmbedding())))
                .filter(sc -> sc.score() >= properties.getSimilarityThreshold())
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(properties.getTopK())
                .toList();

        if (scored.isEmpty()) {
            log.warn("No chunks passed filter + similarity threshold {}", properties.getSimilarityThreshold());
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
     * Rewrites an ambiguous follow-up question into a fully self-contained standalone query
     * suitable for vector similarity search.
     *
     * <p>When a user asks <em>"Does PostgreSQL support it?"</em> after discussing HNSW indexing,
     * the pronoun <em>"it"</em> produces a meaningless embedding. This method resolves such
     * references by asking the LLM to rewrite the question with all context inlined:
     * <em>"Does PostgreSQL support HNSW indexing?"</em>
     *
     * <p>Only the rewritten query is used for retrieval. The original question is preserved
     * for the final {@link #buildMessages} call so the user sees a natural conversation.
     *
     * <p>If history is empty the question is already standalone — returned as-is with no
     * LLM call. If the rewrite LLM call fails, the original question is used as a fallback
     * so retrieval degrades gracefully rather than failing entirely.
     *
     * @param question the raw follow-up question from the user
     * @param history  prior conversation turns used to resolve references
     * @return a standalone question safe to embed for vector search
     */
    private String contextualizeQuestion(String question, List<ConversationTurn> history) {
        if (history.isEmpty()) {
            return question;
        }
        try {
            StringBuilder prompt = new StringBuilder(properties.getContextualizationPrompt());
            history.forEach(t -> prompt.append(t.role()).append(": ").append(t.content()).append("\n"));
            prompt.append("\nFollow-up: ").append(question);

            String rewritten = chatLanguageModel.generate(UserMessage.from(prompt.toString()))
                                                .content().text().strip();
            log.debug("Question contextualized: [{}] → [{}]", question, rewritten);
            return rewritten;
        } catch (Exception e) {
            log.warn("Question contextualization failed, falling back to original: {}", e.getMessage());
            return question;
        }
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
