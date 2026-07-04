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
 * <p>This annotation is placed on the small, concretely-typed listener method that each concrete
 * {@link AsyncEventHandler} subclass declares (see its class Javadoc for why this cannot live on
 * the shared abstract {@code handle(Object)} method instead — in short, Spring cannot resolve a
 * generic event type inherited from a superclass, and CGLIB cannot advise a {@code final}
 * method). That listener method should do nothing but delegate to
 * {@link AsyncEventHandler#handle}; the actual handling logic goes in
 * {@link AsyncEventHandler#doHandle(Object)}.
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
