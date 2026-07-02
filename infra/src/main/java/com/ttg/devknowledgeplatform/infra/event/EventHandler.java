package com.ttg.devknowledgeplatform.infra.event;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composed annotation that marks a method as an asynchronous Spring application event handler.
 *
 * <p>Combining {@link EventListener} and {@link Async} in a single project-specific annotation
 * serves three purposes:
 * <ol>
 *   <li><strong>Consistency</strong> — every handler is automatically asynchronous; forgetting
 *       {@code @Async} on a new listener is no longer possible.</li>
 *   <li><strong>Discoverability</strong> — a single grep for {@code @EventHandler} (or
 *       {@code implements ApplicationEventHandler}) finds every listener, unlike hunting for
 *       two Spring annotations independently across modules.</li>
 *   <li><strong>Isolation</strong> — {@code @Async("asyncEventExecutor")} pins every event
 *       handler to a dedicated pool, separate from the {@code sseStreamExecutor} used for SSE
 *       streaming. This is a bulkhead: SSE requests hold a thread for up to 60&nbsp;s, so
 *       without this isolation a burst of chat traffic could exhaust the shared pool and cause
 *       {@code RejectedExecutionException} to propagate synchronously out of whatever
 *       transactional service published the event (e.g. content publishing) — turning a
 *       capacity problem into a correctness one.</li>
 * </ol>
 *
 * <p>This annotation is placed on {@link AsyncEventHandler#handle(Object)} in the abstract base
 * class. Individual handlers only implement {@link AsyncEventHandler#doHandle(Object)} — they
 * do not need to repeat this annotation.
 *
 * @see AsyncEventHandler
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EventListener
@Async("asyncEventExecutor")
public @interface EventHandler {
}
