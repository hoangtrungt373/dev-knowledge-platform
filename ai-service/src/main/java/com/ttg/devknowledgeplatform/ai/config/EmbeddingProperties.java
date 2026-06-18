package com.ttg.devknowledgeplatform.ai.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.ai.embedding")
@Validated
@Getter
@Setter
public class EmbeddingProperties {

    @NotBlank
    private String apiKey;

    @NotBlank
    private String model = "text-embedding-3-small";

    @Positive
    private int dimensions = 1536;

    /** Target token count per chunk. 1 token ≈ 4 characters. */
    @Positive
    private int chunkSize = 512;

    /** Token overlap between consecutive chunks to preserve context at boundaries. */
    @Positive
    private int chunkOverlap = 100;
}
