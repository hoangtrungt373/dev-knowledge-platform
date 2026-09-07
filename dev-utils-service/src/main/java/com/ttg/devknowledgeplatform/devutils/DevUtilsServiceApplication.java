package com.ttg.devknowledgeplatform.devutils;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

import com.ttg.devknowledgeplatform.common.exception.GlobalExceptionHandler;
import com.ttg.devknowledgeplatform.infra.config.json.JacksonConfig;
import com.ttg.devknowledgeplatform.infra.tracing.TraceContextFilter;

/**
 * Entry point for the standalone {@code dev-utils-service} application — a stateless developer
 * utility belt (JSON format/validate, YAML&harr;JSON conversion, HTML beautify) alongside this
 * reactor's other six standalone services. See root {@code CLAUDE.md}'s module table and this
 * module's own {@code CLAUDE.md} for the full picture.
 *
 * <p>The one deployable in this reactor with genuinely nothing to persist: every operation is a
 * pure text-in/text-out transform, so unlike every other service here there is **no JPA/Postgres
 * dependency, no schema, no Liquibase changelog**. It is *not*, however, free of Spring Security on
 * its classpath — see {@code security.SecurityConfig}'s own Javadoc for why that dependency turned
 * out to be unavoidable even though this module authenticates no one, and how its own filter chain
 * neutralizes Spring Boot's autoconfigured default rather than relying on the dependency's absence.
 *
 * <p>{@code @Import} names the exact {@code infra}/{@code common} beans this module actually uses,
 * same convention every other standalone service in this reactor follows (see root
 * {@code CLAUDE.md}'s "Post-extraction hardening" section for the three-round bug history that
 * established this instead of a broad {@code @ComponentScan} onto {@code infra}):
 * {@link JacksonConfig} (shared {@code ObjectMapper} customization — the JSON format/YAML
 * conversion operations depend on this exact bean) and {@link TraceContextFilter}
 * (distributed-tracing MDC binding + access logging, reactor-wide). {@link GlobalExceptionHandler}
 * is {@code common}'s own {@code @RestControllerAdvice}, imported the same way every other service
 * imports it. No Keycloak-related import, no {@code CurrentUserIdArgumentResolver} — there is no
 * authenticated principal to resolve.
 */
@SpringBootApplication
@Import({JacksonConfig.class, TraceContextFilter.class, GlobalExceptionHandler.class})
public class DevUtilsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevUtilsServiceApplication.class, args);
    }
}
