# RAG Pipeline Improvements

Tracking all planned improvements to the RAG/LLM implementation.
Each step is discussed before implementation and checked off when done.

---

## Step 1 — Similarity Threshold ✅

**Problem:** `findTopSimilarIds` always returns top-K chunks regardless of relevance score.
Irrelevant chunks get passed to the LLM, degrading answer quality.

**What was done:**
- Added `topK` and `similarityThreshold` fields to `EmbeddingProperties`
- Added `top-k` and `similarity-threshold` to `application.yml` (env: `RAG_TOP_K`, `RAG_SIMILARITY_THRESHOLD`)
- Added `.filter(sc -> sc.score() >= properties.getSimilarityThreshold())` in `RagQueryServiceImpl`
- Added early return with `NO_CONTEXT_ANSWER` if all chunks are filtered out

**Files changed:**
- `ai-service/.../config/EmbeddingProperties.java`
- `ai-service/.../service/impl/RagQueryServiceImpl.java`
- `api/src/main/resources/application.yml`

---

## Step 2 — Error Handling ✅

**Problem:** OpenAI API failures bubble up as unhandled 500s. No retry, no fallback.

**What was done:**
- Added `maxRetries` field to `EmbeddingProperties` (env: `AI_MAX_RETRIES`, default: `3`)
- Added `.maxRetries(properties.getMaxRetries())` to `OpenAiChatModel` and `OpenAiEmbeddingModel` builders
- Created `RagQueryException` in `ai-service` extending `ApiException` with `AI_SERVICE_UNAVAILABLE`
- Added `AI_SERVICE_UNAVAILABLE` and `AI_EMBEDDING_FAILED` to `ErrorCode`
- Wrapped `RagQueryServiceImpl.query()` in try-catch — delegates to `doQuery()`, re-throws `RagQueryException`, wraps unknown exceptions

**Files changed:**
- `ai-service/.../config/AiServiceConfig.java`
- `ai-service/.../service/impl/OpenAiEmbeddingServiceImpl.java`
- `ai-service/.../exception/RagQueryException.java` *(new)*
- `ai-service/.../service/impl/RagQueryServiceImpl.java`
- `common/.../exception/ErrorCode.java`
- `api/src/main/resources/application.yml`

---

## Step 3 — Rate Limiting ✅

**Problem:** `/api/v1/chat` has no throttling. Any authenticated user can spam the endpoint
and drive up OpenAI costs or hit OpenAI's own rate limits for everyone.

**What was done:**
- Added `bucket4j-redis` dependency (Bucket4j 8.10.1)
- Created `RateLimitProperties` bound to `app.ai.rate-limit` (env: `RATE_LIMIT_RPM`, `RATE_LIMIT_RPH`)
- Added `RATE_LIMIT_EXCEEDED` to `ErrorCode` → `429 Too Many Requests`
- Created `RateLimitExceededException` in `common` extending `ApiException`
- Added `handleRateLimit` to `GlobalExceptionHandler` returning `429` with `Retry-After: 60` header
- Created `ChatRateLimiter` — per-user Redis-backed token bucket with two limits (per-minute + per-hour)
- Declared `bucket4jRedisConnection` bean in `RedisCacheConfig` (`StatefulRedisConnection<String, byte[]>`)
- Added `@ConfigurationPropertiesScan` to `DevKnowledgePlatformApplication`
- Updated `ChatEndpoint` to call `rateLimiter.consume(authentication.getName())` before RAG query

**Design decisions:**
- Redis-backed (not in-memory) so limits are shared across all app instances
- Two simultaneous limits: per-minute (burst protection) + per-hour (cost cap)
- Greedy refill — tokens restore continuously, not in one burst at window reset
- Keys expire after 2 hours of inactivity — no memory leak
- Key namespace: `rate:chat:{userId}`

**Files changed:**
- `pom.xml` *(bucket4j-redis in dependencyManagement)*
- `api/pom.xml` *(bucket4j-redis dependency)*
- `api/src/main/resources/application.yml`
- `api/.../config/RateLimitProperties.java` *(new)*
- `api/.../config/ChatRateLimiter.java` *(new)*
- `api/.../config/RedisCacheConfig.java` *(bucket4jRedisConnection bean)*
- `api/.../endpoint/ChatEndpoint.java`
- `api/.../DevKnowledgePlatformApplication.java` *(@ConfigurationPropertiesScan)*
- `common/.../exception/ErrorCode.java`
- `common/.../exception/RateLimitExceededException.java` *(new)*
- `common/.../exception/GlobalExceptionHandler.java`

---

## Step 4 — Streaming Responses ✅

**Problem:** LLM answers take 3–10 seconds. The HTTP response is fully buffered before
returning — users see a blank wait with no feedback.

**What was done:**
- Added `StreamingChatLanguageModel` bean to `AiServiceConfig` (no `maxRetries` — retrying mid-stream is not meaningful)
- Created `RagStreamHandler` callback interface in `ai-service` — decouples AI service from HTTP concerns
- Added `queryStream(String question, RagStreamHandler handler)` to `RagQueryService`
- Refactored `RagQueryServiceImpl` — extracted `retrieveAndScore()`, `buildSources()`, `buildContext()` shared private methods; implemented `queryStream()` using `StreamingResponseHandler`
- Added `POST /api/v1/chat/stream` to `ChatEndpoint` returning `SseEmitter` with `text/event-stream`
- Pipeline runs in `CompletableFuture.runAsync()` so HTTP thread returns `SseEmitter` immediately

**SSE event structure:**
- `event: sources` — JSON array of retrieved chunks, sent before LLM generation begins
- `event: token` — one event per generated token
- `event: done` — signals stream is complete

**SSE configuration (same step):**
- Created `AsyncConfig` with `ragStreamExecutor` bean (`ThreadPoolTaskExecutor`: core=10, max=50, queue=100)
- Moved `@EnableAsync` from `DevKnowledgePlatformApplication` to `AsyncConfig`
- `AsyncConfig` implements `WebMvcConfigurer.configureAsyncSupport` — sets 60 s timeout and registers executor
- `AsyncConfig.SSE_TIMEOUT_MS = 60_000L` used as single source of truth for both MVC and emitter timeouts
- `ChatEndpoint.chatStream` registers `emitter.onCompletion / onTimeout / onError` — each sets `AtomicBoolean cancelled`
- `onToken` and `onComplete` check `cancelled` before sending — prevents writing to a dead connection
- `onError` guards against double-complete after client disconnect
- `CompletableFuture.runAsync` now uses `ragStreamExecutor` instead of `ForkJoinPool.commonPool()`

**Design decisions:**
- Existing `POST /api/v1/chat` is untouched — non-SSE clients keep working
- `ScoredChunk` promoted to class-level private record (shared between `doQuery` and `queryStream`)
- Client disconnect (IOException on `emitter.send`) sets `cancelled` and calls `emitter.completeWithError`
- Executor sized for I/O-bound LLM work: core threads always ready, burst capacity via queue then additional threads
- Graceful shutdown: `setWaitForTasksToCompleteOnShutdown(true)` + 30 s `awaitTermination` so active streams finish

**Files changed:**
- `ai-service/.../config/AiServiceConfig.java`
- `ai-service/.../service/RagStreamHandler.java` *(new)*
- `ai-service/.../service/RagQueryService.java`
- `ai-service/.../service/impl/RagQueryServiceImpl.java`
- `api/.../config/AsyncConfig.java` *(new)*
- `api/.../endpoint/ChatEndpoint.java`
- `api/.../DevKnowledgePlatformApplication.java` *(@EnableAsync removed)*

---

## Step 5 — Conversation Memory ⬜

**Problem:** Each request is stateless. Users cannot ask follow-up questions
like "can you show a code example of that?".

**Plan:**
- Create `ChatSession` entity to store conversation history (question + answer pairs)
- Store sessions in PostgreSQL with a TTL or max-turn limit
- Pass previous turns as additional context messages to the LLM
- Expose session ID in request/response so the client can maintain conversation continuity
- Add `GET /api/v1/chat/sessions/{id}` to retrieve history

**Design considerations:**
- Max turns to include in context (suggested: last 5) to avoid hitting token limits
- Session expiry (suggested: 24 hours of inactivity)
- Whether to store full chunk sources per turn or just question/answer

**Files to change (new):**
- `common/.../entity/ChatSession.java`
- `common/.../entity/ChatMessage.java`
- `api/.../repository/ChatSessionRepository.java`
- `api/.../service/ChatSessionService.java`
- `api/.../dto/chat/ChatRequest.java` *(add optional sessionId)*
- `api/.../dto/chat/ChatResponse.java` *(add sessionId)*
- `api/.../endpoint/ChatEndpoint.java`
- DB migration SQL

---

## Step 6 — Metrics & Observability ⬜

**Problem:** No visibility into embedding latency, retrieval time, or LLM response time.
Impossible to diagnose performance issues or cost spikes.

**Plan:**
- Add Micrometer timers around each pipeline stage:
  - `rag.embed.duration` — time to embed the question
  - `rag.retrieve.duration` — time for pgvector similarity search
  - `rag.generate.duration` — time for LLM response generation
- Add counters:
  - `rag.query.total` — total queries
  - `rag.query.no_context` — queries that returned no relevant chunks
  - `rag.query.error` — failed queries
  - `rag.rate_limit.exceeded` — rate limit hits per user
- Expose via existing Actuator `/actuator/metrics` endpoint

**Files to change:**
- `ai-service/.../service/impl/RagQueryServiceImpl.java`
- `api/.../config/ChatRateLimiter.java`

---

## Step 7 — Model Version Guard ⬜

**Problem:** If the embedding model is changed (e.g. from `text-embedding-3-small` to
`text-embedding-3-large`), old stored embeddings are incompatible with new question embeddings,
producing silently wrong similarity scores.

**Plan:**
- Store the embedding model name alongside each `ContentEmbedding` row
- On query, assert that the configured model matches stored embeddings
- On model change, block queries and require a full re-ingestion
- Add an admin endpoint to trigger re-ingestion of all content

**Files to change:**
- `common/.../entity/ContentEmbedding.java` *(add embeddingModel column)*
- DB migration SQL
- `ai-service/.../service/impl/RagQueryServiceImpl.java`
- `ai-service/.../service/impl/ContentIngestionServiceImpl.java`

---

## Step 8 — Tests ⬜

**Problem:** Zero test coverage on the RAG pipeline. No validation of similarity
calculations, threshold filtering, error handling, or rate limiting.

**Plan:**
- Unit tests for `RagQueryServiceImpl`:
  - Happy path: chunks found, threshold passed, LLM called
  - No embeddings in DB → returns `NO_CONTEXT_ANSWER`
  - All chunks below threshold → returns `NO_CONTEXT_ANSWER`
  - OpenAI failure after retries → throws `RagQueryException`
- Unit tests for `ChatRateLimiter`:
  - Requests within limit → allowed
  - Requests exceeding per-minute limit → `RateLimitExceededException`
  - Requests exceeding per-hour limit → `RateLimitExceededException`
- Integration test for `ChatEndpoint`:
  - Authenticated request → 200
  - Rate limit exceeded → 429 with `Retry-After` header
  - OpenAI down → 503

**Files to change (new):**
- `ai-service/src/test/.../RagQueryServiceImplTest.java`
- `api/src/test/.../ChatRateLimiterTest.java`
- `api/src/test/.../ChatEndpointTest.java`
