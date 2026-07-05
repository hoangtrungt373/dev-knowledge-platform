package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.config.LabelsConfig;
import com.ttg.devknowledgeplatform.ai.config.LoadedPrompts;
import com.ttg.devknowledgeplatform.ai.service.ChatModelResolver;
import com.ttg.devknowledgeplatform.ai.service.ConversationSummarisationService;
import com.ttg.devknowledgeplatform.common.dto.ConversationTurn;
import dev.langchain4j.data.message.UserMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Default {@link ConversationSummarisationService} implementation backed by the server's
 * default blocking chat model ({@code ChatModelResolver.resolveBlocking(null)}) — history
 * compression is an internal pipeline utility call, not part of the user-facing answer
 * generation that {@code ChatRequest.chatModel} controls, so it always runs on one fixed model.
 *
 * <p>The prompt is assembled in three parts:
 * <ol>
 *   <li>The configurable summarisation prompt loaded from {@code prompts/summarisation-prompt.txt}
 *       (instructs the model on the desired output format and length).</li>
 *   <li>An optional "Previous summary" section so the model can build on, rather than
 *       replace, the existing compressed history.</li>
 *   <li>The new turns, formatted as {@code ROLE: content} lines.</li>
 * </ol>
 *
 * <p>On any LLM error the previous summary is returned unchanged so the session
 * continues to function correctly — summarisation degrades gracefully.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class ConversationSummarisationServiceImpl implements ConversationSummarisationService {

    private final ChatModelResolver chatModelResolver;
    private final LoadedPrompts prompts;
    private final LabelsConfig labels;

    @Override
    public String summarise(String previousSummary, List<ConversationTurn> turnsToCompress) {
        StringBuilder prompt = new StringBuilder(prompts.summarisation());

        if (previousSummary != null && !previousSummary.isBlank()) {
            prompt.append(labels.getCompressionPreviousSummaryLabel())
                  .append(previousSummary);
        }

        prompt.append(labels.getCompressionTurnsLabel());
        turnsToCompress.forEach(t ->
                prompt.append(t.role()).append(": ").append(t.content()).append("\n"));

        try {
            String result = chatModelResolver.resolveBlocking(null).generate(UserMessage.from(prompt.toString()))
                                             .content().text().strip();
            log.debug("Generated rolling summary: {} chars from {} turns",
                    result.length(), turnsToCompress.size());
            return result;
        } catch (Exception e) {
            log.warn("Summarisation LLM call failed — keeping previous summary: {}", e.getMessage());
            return previousSummary != null ? previousSummary : "";
        }
    }
}
