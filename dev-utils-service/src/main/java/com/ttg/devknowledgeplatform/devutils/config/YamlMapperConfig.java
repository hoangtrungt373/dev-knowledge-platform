package com.ttg.devknowledgeplatform.devutils.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Shared {@link YAMLMapper} bean — the YAML-side counterpart to {@code infra}'s shared
 * {@code ObjectMapper} (see {@code infra.config.json.JacksonConfig}), for the same reason: one
 * Spring-managed instance instead of every consumer constructing its own. Mirrors that bean's
 * customization ({@link JavaTimeModule}, tolerant deserialization, ISO-8601 dates) even though
 * neither of this module's operations can currently observe a difference — both only ever call
 * {@code readTree}/{@code writeValueAsString} against a generic {@code JsonNode} tree, never a
 * POJO with an {@code Instant}/{@code LocalDate} field. Kept consistent anyway so a future
 * operation that does deserialize into a typed object doesn't hit a silent inconsistency between
 * the JSON and YAML mappers.
 *
 * <p>Lives here, not {@code infra} — this module is the only consumer today; promote it there only
 * once a second module genuinely needs the same bean (see {@code infra/CLAUDE.md}'s own rule for
 * when a utility is worth sharing).
 */
@Configuration
public class YamlMapperConfig {

    @Bean
    public YAMLMapper yamlMapper() {
        // ObjectMapper's own registerModule()/disable() are declared to return ObjectMapper, not a
        // covariant self-type, so chaining through them here would silently narrow the static type
        // back to ObjectMapper even though the runtime object is still a YAMLMapper. Mutating a
        // local YAMLMapper-typed variable statement-by-statement instead keeps the declared return
        // type intact.
        YAMLMapper mapper = YAMLMapper.builder().build();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
