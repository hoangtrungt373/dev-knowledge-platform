# Project Structure

## Module layout

```
dev-knowledge-platform/
├── common/          — shared entities, enums, exceptions, DTOs; no Spring dependencies
├── ai-service/      — RAG pipeline: embedding, vector search, LLM generation (LangChain4j)
├── api/             — REST endpoints, security, Liquibase migrations, Spring Boot entry point
└── gui/             — React 18 + TypeScript + MUI frontend (Vite)
```

Dependency order: `common` ← `ai-service` ← `api`. `gui` is independent.

---

## common

```
common/src/main/java/com/ttg/devknowledgeplatform/common/
├── dto/
│   └── ConversationTurn.java         — role + content record for conversation history
├── entity/
│   ├── AbstractEntity.java           — audit columns (usrCreation, dteCreation, version, …)
│   ├── Article.java
│   ├── Category.java                 — hierarchical; parent/children self-join
│   ├── ContentItem.java              — base content record (type, status, title, slug, category)
│   ├── ContentItemTag.java           — join entity for content ↔ tag
│   ├── InterviewQuestion.java
│   └── Tag.java
├── enums/
│   ├── ContentStatus.java            — DRAFT, PUBLISHED, …
│   ├── ContentType.java              — INTERVIEW_QUESTION, ARTICLE, BLOG_POST
│   └── UserRole.java
└── exception/
    ├── ApiException.java
    ├── BusinessException.java
    ├── ErrorCode.java                — 88+ domain error codes
    └── ResourceNotFoundException.java
```

---

## ai-service

```
ai-service/src/main/java/com/ttg/devknowledgeplatform/ai/
├── config/
│   ├── AiServiceConfig.java          — wires ChatLanguageModel + StreamingChatLanguageModel beans
│   └── EmbeddingProperties.java      — @ConfigurationProperties at app.ai.embedding.*
│                                        fields: apiKey, model, dimensions, chunkSize, chunkOverlap,
│                                        chatModel, maxTokens, temperature, maxRetries,
│                                        topK, similarityThreshold, oversampleFactor
├── converter/
│   └── FloatArrayToVectorConverter.java  — JPA AttributeConverter for pgvector column type
├── dto/
│   ├── RagAnswer.java                — answer text + List<RagSource>
│   └── RagSource.java                — contentItemId, sourceType, title, chunkText, similarity
├── entity/
│   └── ContentEmbedding.java         — embedding vector (1536-dim), chunkText, sourceType,
│                                        chunkIndex, modelName, tokenCount,
│                                        metadata (JSONB: categoryId, categoryName, tagIds, tagNames)
├── exception/
│   └── RagQueryException.java
├── filter/                           — dynamic post-retrieval filter package
│   ├── RagFilter.java                — Java 21 record: sourceTypes, tags, categoryId
│   ├── RagFilterStrategy.java        — interface: predicate(RagFilter) + isApplicable(RagFilter)
│   ├── SourceTypeFilterStrategy.java — filters by ContentType column
│   ├── MetadataTagFilterStrategy.java    — filters by tagNames[] in JSONB (any-match)
│   └── MetadataCategoryFilterStrategy.java — filters by categoryId in JSONB (exact)
├── repository/
│   └── ContentEmbeddingRepository.java   — findTopSimilarIds (pgvector <=>), findAllByIdWithContentItem
└── service/
    ├── ContentIngestionService.java   — chunks text + stores embeddings
    ├── EmbeddingService.java          — wraps OpenAI embedding API
    ├── RagQueryService.java           — interface: query() + queryStream() with filter overloads
    ├── RagStreamHandler.java          — SSE callback interface
    └── impl/
        └── RagQueryServiceImpl.java   — orchestrates the full RAG pipeline;
                                         injects List<RagFilterStrategy> for dynamic filtering
```

---

## api

```
api/src/main/java/com/ttg/devknowledgeplatform/
├── api/                              — controller interfaces (HTTP annotations live here)
│   └── impl/
│       ├── ChatController.java       — POST /api/v1/chat, POST /api/v1/chat/stream;
│       │                               builds RagFilter from ChatRequest and passes to RagQueryService
│       └── …                         — other controllers
├── config/
│   ├── AsyncConfig.java              — ragStreamExecutor thread pool (10 core, 50 max, queue 100)
│   ├── SecurityConfig.java           — JWT + OAuth2 filter chain
│   └── sse/
│       └── SseStreamTemplate.java    — SSE writer abstraction
├── database/
│   └── sql/                          — Liquibase changelogs (master: dev-knowledge-platform.xml)
├── dto/
│   └── chat/
│       ├── ChatRequest.java          — question, sessionId, sourceTypes, categoryId, tags
│       ├── ChatResponse.java
│       ├── ChatSessionHistoryDto.java
│       └── ChatSessionSummaryDto.java
├── mapper/                           — MapStruct mappers (DTO ↔ entity)
├── repository/
│   ├── spec/                         — JPA Specification implementations for dynamic filtering
│   └── …
├── security/                         — JwtProvider, OAuth2 handlers, UserUtils
└── service/
    ├── ChatSessionService.java
    ├── ContentIndexingService.java   — index / reindex / deleteIndex per contentItemId
    └── impl/
        └── ContentIndexingServiceImpl.java  — type-specific ingestion; buildCommonMetadata()
                                               writes categoryId, categoryName, tagIds, tagNames
                                               to every chunk's JSONB metadata
```

---

## Request flow

```
GUI (React)
  └─→ ChatController (POST /api/v1/chat[/stream])
        builds RagFilter from request fields
        └─→ RagQueryService
              contextualizeQuestion (LLM rewrite if history present)
              └─→ EmbeddingService (OpenAI text-embedding-3-small)
              └─→ ContentEmbeddingRepository.findTopSimilarIds (pgvector <=>)
                   oversample by oversampleFactor when RagFilter is non-empty
              └─→ ContentEmbeddingRepository.findAllByIdWithContentItem
              └─→ RagFilterStrategy composition (Predicate<ContentEmbedding>)
              └─→ dotProduct scoring + similarityThreshold filter + topK cut
              └─→ StreamingChatLanguageModel (gpt-4o-mini) — SSE token stream
```

---

## Database

- Schema: `product`
- Sequences: one per table (`TABLE_NAME_SEQ`)
- Audit columns on every entity via `AbstractEntity`
- pgvector HNSW index on `content_embedding.embedding` (cosine distance, `vector_cosine_ops`)
- Migrations: `api/src/main/java/com/ttg/devknowledgeplatform/database/sql/`
  - Naming: `YYYY/VERSION/YYYYMMDDHHMI__VERSION__TICKET__description.sql`
