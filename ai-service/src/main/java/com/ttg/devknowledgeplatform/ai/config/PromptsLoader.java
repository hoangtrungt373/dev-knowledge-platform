package com.ttg.devknowledgeplatform.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Loads system prompt and utility prompt text from classpath files and exposes them
 * as a single {@link LoadedPrompts} bean.
 *
 * <p>Prompts live in {@code ai-service/src/main/resources/prompts/} as plain {@code .txt} files.
 * Moving them out of {@code application.yml} eliminates ~150 lines of YAML noise, allows
 * syntax highlighting and clean diffs in version control, and makes prompt authoring
 * accessible without touching configuration files.
 *
 * <p>The file paths are not configurable at runtime by design — prompts are static content
 * that require code review to change, not operational tuning.
 */
@Configuration
public class PromptsLoader {

    @Bean
    public LoadedPrompts loadedPrompts() throws IOException {
        return new LoadedPrompts(
                load("prompts/system-prompt.txt"),
                load("prompts/system-prompt-article.txt"),
                load("prompts/system-prompt-question-answer.txt"),
                load("prompts/system-prompt-blog-post.txt"),
                load("prompts/input-enrichment-prompt.txt"),
                load("prompts/summarisation-prompt.txt")
        );
    }

    private String load(String path) throws IOException {
        var resource = new ClassPathResource(path);
        try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            return FileCopyUtils.copyToString(reader);
        }
    }
}
