package com.ttg.devknowledgeplatform.infra.config.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Shared {@link ObjectMapper} customization — {@code Instant}/{@code LocalDate}-friendly JSON via
 * {@link JavaTimeModule}, tolerant deserialization (unknown properties don't fail a request), and
 * ISO-8601 dates instead of epoch-millis timestamps.
 *
 * <p>Moved here from {@code gateway} once every standalone service's own {@code @ComponentScan}
 * was widened to reach this module's sibling package (see each service's own
 * {@code @SpringBootApplication} Javadoc) — before that, this bean only ever applied to
 * {@code gateway}'s own (nonexistent, since it has no REST controllers) JSON serialization, while
 * every one of the six standalone services silently fell back to Spring Boot's un-customized
 * default {@code ObjectMapper} instead. Living here means any service whose component scan reaches
 * {@code infra} — which is now all seven apps in this reactor — picks it up automatically, with no
 * per-service {@code JacksonConfig} duplicate needed.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.build()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
