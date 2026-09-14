package com.ttg.devknowledgeplatform.devutils;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Import;

import com.ttg.devknowledgeplatform.common.exception.GlobalExceptionHandler;
import com.ttg.devknowledgeplatform.infra.config.json.JacksonConfig;
import com.ttg.devknowledgeplatform.infra.tracing.TraceContextFilter;

/**
 * Entry point for the standalone {@code dev-utils-service} application — a stateless developer
 * utility belt now spanning all five {@link com.ttg.devknowledgeplatform.devutils.service.OperationGroup}
 * categories (Formatters: JSON/YAML/HTML/CSS/LESS/SCSS/JS/ERB/XML/SQL/CSV/PHP-array/String-Case/
 * Unix-Time; Encoders/Decoders: Base64/URL/HTML-entity/PHP-serialize/ASCII-Hex/Hash; Inspectors:
 * JWT Debugger/RegExp Tester/URL Parser/Cron Parser/Text Diff; Web: HTML &amp; Markdown Preview/
 * HTML to TSX/Color Converter/SVG to CSS; Generators: Lorem Ipsum) alongside this reactor's other
 * six standalone services — see this module's own {@code CLAUDE.md} for the full, current
 * operation list (this summary is deliberately not re-derived here on every addition; check
 * {@code service/impl/*Operation.java} directly if this drifts). See root {@code CLAUDE.md}'s
 * module table for the full picture.
 *
 * <p>The one deployable in this reactor with genuinely nothing to persist: every operation is a
 * pure text-in/text-out transform, so unlike every other service here there is **no JPA/Postgres
 * dependency, no schema, no Liquibase changelog**. It is *not*, however, free of Spring Security on
 * its classpath — see {@code security.SecurityConfig}'s own Javadoc for why that dependency turned
 * out to be unavoidable even though this module authenticates no one, and how its own filter chain
 * neutralizes Spring Boot's autoconfigured default rather than relying on the dependency's absence.
 *
 * <p><b>{@code exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class}}
 * is not optional — found by an actual boot attempt, not anticipated up front.</b> {@code common}
 * declares {@code spring-boot-starter-data-jpa} as a non-optional dependency (needed there for
 * {@code AbstractEntity}'s {@code @MappedSuperclass}/{@code @Entity} annotation support), which
 * every consumer of {@code common} inherits transitively — including this module, even though it
 * maps zero entities. Every other service in this reactor never notices, because each one already
 * configures a real {@code spring.datasource.url} and declares {@code org.postgresql:postgresql}
 * at runtime; this module deliberately has neither. Left un-excluded, Spring Boot's
 * {@code DataSourceAutoConfiguration} still tries to build a {@code HikariDataSource} regardless of
 * whether anything needs an {@code EntityManagerFactory}, and fails outright
 * ({@code DataSourceBeanCreationException: Failed to determine a suitable driver class}) before
 * this app ever starts serving traffic — confirmed via a real {@code @SpringBootTest} context-load
 * failure, not a hypothetical.
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
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class})
@Import({JacksonConfig.class, TraceContextFilter.class, GlobalExceptionHandler.class})
public class DevUtilsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevUtilsServiceApplication.class, args);
    }
}
