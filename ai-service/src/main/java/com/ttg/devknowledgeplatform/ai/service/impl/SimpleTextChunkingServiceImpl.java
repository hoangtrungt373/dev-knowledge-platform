package com.ttg.devknowledgeplatform.ai.service.impl;

import com.ttg.devknowledgeplatform.ai.config.EmbeddingProperties;
import com.ttg.devknowledgeplatform.ai.service.TextChunkingService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Recursive text chunker backed by LangChain4j's {@link DocumentSplitters#recursive}.
 *
 * Tries separators in order: paragraph breaks (\n\n) → line breaks (\n) →
 * sentence endings (". ") → word boundaries (" ") → characters.
 * Falls back to the next separator only when a chunk still exceeds the size limit,
 * so natural boundaries (paragraphs, sentences) are preserved wherever possible.
 */
@Service
@Slf4j
public class SimpleTextChunkingServiceImpl implements TextChunkingService {

    private static final int CHARS_PER_TOKEN = 4;

    private final DocumentSplitter splitter;

    public SimpleTextChunkingServiceImpl(EmbeddingProperties properties) {
        int maxSegmentSize = properties.getChunkSize() * CHARS_PER_TOKEN;
        int maxOverlapSize = properties.getChunkOverlap() * CHARS_PER_TOKEN;
        this.splitter = DocumentSplitters.recursive(maxSegmentSize, maxOverlapSize);
        log.info("TextChunkingService initialised: maxSegmentSize={}chars maxOverlapSize={}chars",
                maxSegmentSize, maxOverlapSize);
    }

    @Override
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) return List.of();

        List<TextSegment> segments = splitter.split(Document.from(text.trim()));
        List<String> chunks = segments.stream()
                .map(TextSegment::text)
                .filter(s -> !s.isBlank())
                .toList();

        log.debug("Chunked {} chars → {} chunks", text.length(), chunks.size());
        return chunks;
    }

    @Override
    public int estimateTokens(String text) {
        if (text == null) return 0;
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }
}
