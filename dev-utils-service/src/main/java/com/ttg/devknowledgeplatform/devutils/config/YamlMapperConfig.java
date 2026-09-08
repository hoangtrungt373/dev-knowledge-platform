package com.ttg.devknowledgeplatform.devutils.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
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
 * <p><b>3 {@link YAMLGenerator.Feature} overrides fix a real bug, reported directly against a real
 * payload — {@code YAMLMapper.builder().build()}'s own stock defaults diverge from conventional
 * YAML output (the same "not a style preference" bug class {@code ConventionalJsonPrettyPrinter}
 * fixes on the JSON-pretty-print side, same module, verified the same way: a real before/after
 * comparison, not assumed from memory).</b>
 * <ul>
 *   <li>{@code WRITE_DOC_START_MARKER} (default {@code true}) — writes a leading {@code ---}
 *       document-start marker; disabled, since a single, standalone YAML document (this operation
 *       never emits a multi-document stream) doesn't need one, and no mainstream YAML formatter
 *       adds it unprompted.</li>
 *   <li>{@code MINIMIZE_QUOTES} (default {@code false}) — Jackson quotes every string scalar by
 *       default (type-fidelity safety: an unquoted {@code true}/{@code 123} could round-trip back
 *       as a boolean/number instead of a string), even when the value is unambiguously safe to
 *       leave bare (e.g. {@code Vui Coding}). Enabled, so a plain string renders unquoted whenever
 *       SnakeYAML can do so safely — the value that actually *needs* quoting (already ambiguous
 *       once parsed back, e.g. a string that's literally {@code "true"}) still gets it either way,
 *       this only removes quoting from values that never needed it.</li>
 *   <li>{@code INDENT_ARRAYS_WITH_INDICATOR} (default {@code false}) — Jackson's own stock output
 *       renders a block sequence with its {@code -} indicator at the *same* column as the parent
 *       key ({@code features:\n- tools}), not indented under it. Enabling plain
 *       {@code INDENT_ARRAYS} instead was tried first and rejected — measured (not assumed) via a
 *       real before/after comparison to only indent the indicator by 1 space
 *       ({@code features:\n - tools}), not the conventional 2-space block indent every mainstream
 *       YAML formatter uses. {@code INDENT_ARRAYS_WITH_INDICATOR} folds the indicator's own width
 *       into the indent calculation instead, producing the expected
 *       {@code features:\n  - tools}.</li>
 * </ul>
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
        YAMLMapper mapper = YAMLMapper.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
                .build();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
