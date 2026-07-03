package com.ttg.devknowledgeplatform.ai.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;

/**
 * Resolves a chat model id (as sent via {@code ChatRequest.chatModel()}) to the LangChain4j
 * model instance that should serve the request.
 *
 * <p>Backs the runtime chat-model-switch feature: {@code AiServiceConfig} builds one
 * {@link ChatLanguageModel}/{@link StreamingChatLanguageModel} pair per configured
 * {@code ChatModelsConfig.ChatModelProfile}; this resolver is the single place that looks one up
 * by id, so {@code RagQueryServiceImpl} never needs to know which provider is behind a given id.
 */
public interface ChatModelResolver {

    /**
     * @param modelId requested model id, or {@code null} to use the configured default
     * @return the blocking chat model for {@code modelId}
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException with
     *         {@code ErrorCode.AI_MODEL_UNSUPPORTED} if {@code modelId} does not match any
     *         configured profile
     */
    ChatLanguageModel resolveBlocking(String modelId);

    /**
     * @param modelId requested model id, or {@code null} to use the configured default
     * @return the streaming chat model for {@code modelId}
     * @throws com.ttg.devknowledgeplatform.common.exception.BusinessException with
     *         {@code ErrorCode.AI_MODEL_UNSUPPORTED} if {@code modelId} does not match any
     *         configured profile
     */
    StreamingChatLanguageModel resolveStreaming(String modelId);

    /**
     * Resolves the effective model id — the one that will actually be invoked — without
     * touching either bean map. Used to record which model served a request (e.g. onto
     * {@code RagPipelineContext}) before generation happens, so it's available even if
     * generation itself is never reached.
     *
     * @param modelId requested model id, or {@code null}
     * @return {@code modelId} if non-null, otherwise the configured default model id
     */
    String resolveModelId(String modelId);
}
