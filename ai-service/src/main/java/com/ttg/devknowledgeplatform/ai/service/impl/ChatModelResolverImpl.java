package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.config.ChatModelsConfig;
import com.ttg.devknowledgeplatform.ai.exception.AiErrorCode;
import com.ttg.devknowledgeplatform.ai.service.ChatModelResolver;
import com.ttg.devknowledgeplatform.common.exception.Validator;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Default {@link ChatModelResolver}. Looks up the requested model id in the bean maps built by
 * {@code AiServiceConfig} from {@link ChatModelsConfig}; falls back to
 * {@link ChatModelsConfig#getDefaultModel()} when the request does not specify one.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Throwable.class)
public class ChatModelResolverImpl implements ChatModelResolver {

    private final Map<String, ChatLanguageModel> chatLanguageModels;
    private final Map<String, StreamingChatLanguageModel> streamingChatLanguageModels;
    private final ChatModelsConfig chatModelsConfig;

    @Override
    public ChatLanguageModel resolveBlocking(String modelId) {
        String resolvedId = resolveModelId(modelId);
        return Validator.notNull(chatLanguageModels.get(resolvedId), AiErrorCode.AI_MODEL_UNSUPPORTED, modelId);
    }

    @Override
    public StreamingChatLanguageModel resolveStreaming(String modelId) {
        String resolvedId = resolveModelId(modelId);
        return Validator.notNull(streamingChatLanguageModels.get(resolvedId), AiErrorCode.AI_MODEL_UNSUPPORTED, modelId);
    }

    @Override
    public String resolveModelId(String modelId) {
        return modelId != null ? modelId : chatModelsConfig.getDefaultModel();
    }
}
