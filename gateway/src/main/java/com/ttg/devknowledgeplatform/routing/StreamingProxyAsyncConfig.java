package com.ttg.devknowledgeplatform.routing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Async-dispatch wiring for any hand-rolled streaming proxy in this app — today that's just
 * {@link ChatStreamProxyController}, but the name is deliberately generic, not
 * {@code ChatStreamAsyncConfig}: {@code configureAsyncSupport}'s {@code setTaskExecutor} sets
 * *the one* default executor for this whole Spring MVC context, so a future second
 * {@code StreamingResponseBody}-based endpoint would start using this same bean automatically,
 * regardless of what it's called — a chat-specific name would have quietly become inaccurate the
 * moment that happened, rather than describing the mechanism it actually governs.
 *
 * <p>{@code StreamingResponseBody}'s own Javadoc explicitly recommends configuring a dedicated
 * {@code TaskExecutor} rather than relying on Spring MVC's default (an unbounded
 * {@code SimpleAsyncTaskExecutor} that creates a new thread per request) — this app has exactly
 * one async-dispatched endpoint today, so this bean exists solely to give it a bounded pool
 * instead of unbounded thread creation under load.
 *
 * <p>The 60-second timeout must stay in sync with {@code ai-service}'s own
 * {@code SseStreamTemplate.SSE_TIMEOUT_MS} — see {@link ChatStreamProxyController}'s own
 * {@code UPSTREAM_TIMEOUT} constant and Javadoc for why. This is a reactor-wide default (applies
 * to every async-dispatched request in this app), which is safe today only because
 * {@code ChatStreamProxyController} is the sole consumer — revisit if a second async endpoint
 * with a genuinely different timeout need ever lands here.
 */
@Configuration
public class StreamingProxyAsyncConfig implements WebMvcConfigurer {

    private static final long ASYNC_TIMEOUT_MS = 60_000L;

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(ASYNC_TIMEOUT_MS);
        configurer.setTaskExecutor(streamRelayExecutor());
    }

    @Bean
    public ThreadPoolTaskExecutor streamRelayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("stream-relay-");
        executor.initialize();
        return executor;
    }
}
