package com.ttg.devknowledgeplatform.infra.event;

/**
 * Marker interface for Spring application event listeners in this project.
 *
 * <p>Every bean that listens for domain events must implement this interface. It has no
 * methods — its sole purpose is discoverability:
 * <ul>
 *   <li>In an IDE: <em>Find Implementations</em> on this interface lists every event
 *       listener across all modules in one view.</li>
 *   <li>In code review or grep: {@code implements ApplicationEventHandler} is a
 *       clearer signal than {@code @EventListener} hidden inside a service class.</li>
 * </ul>
 *
 * <p>Concrete listeners should extend {@link AsyncEventHandler} (which implements this
 * interface) rather than implementing it directly — the abstract class provides async
 * dispatch, MDC propagation, timing, and exception safety.
 *
 * @see AsyncEventHandler
 * @see EventHandler
 */
public interface ApplicationEventHandler {
}
