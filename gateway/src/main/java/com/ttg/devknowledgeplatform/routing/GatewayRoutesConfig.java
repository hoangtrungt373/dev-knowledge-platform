package com.ttg.devknowledgeplatform.routing;

import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import lombok.RequiredArgsConstructor;

/**
 * Proxies every external client request this app now fronts to the standalone service that
 * actually owns it, via Spring Cloud Gateway Server MVC (the servlet/blocking variant — matches
 * every other module's stack; the original Spring Cloud Gateway is WebFlux-based).
 *
 * <p>One {@code @Bean} per backend service, each forwarding a fixed set of path patterns to that
 * service's {@link GatewayServicesProperties} base URL, unchanged (no prefix stripping/rewriting)
 * — every backend already expects exactly the path it's called with, since the GUI called these
 * same paths directly before this gateway existed.
 *
 * <p><b>Path patterns are resource-specific, not top-level-prefix-based, because three prefixes are
 * shared by more than one service and only disambiguate one segment deeper:</b> {@code
 * /api/v1/users} (this module owns {@code /me/**}, {@code social-service} owns {@code
 * /public/**}/{@code /search}), {@code /api/v1/public} ({@code content-service} owns {@code
 * /question-answers/**}/{@code /articles/**}, {@code ecommerce-service} owns {@code
 * /products/**}, {@code /product-categories/**} — added for the storefront's category filter rail,
 * since a logged-out shopper can't reach the admin-gated
 * {@code /api/v1/admin/product-categories/**} — and {@code /payment-config} (tells the checkout
 * GUI at runtime whether to render Stripe Elements)), and {@code /api/v1/admin} (three different
 * services, but each one's own resource segment — {@code /products/**}, {@code
 * /product-categories/**}, {@code /product-tags/**}, {@code /product-attributes/**}, {@code
 * /orders/**}, {@code /articles/**}, {@code /embeddings/**}, etc. — never collides with another's).
 * Confirmed via a full audit of
 * every {@code @RequestMapping} in the reactor before writing this class, not assumed from the
 * top-level prefix alone. <b>Caveat, learned the hard way:</b> that audit is only as good as
 * re-running it every time a new admin resource is added — {@code /api/v1/admin/product-tags/**}
 * shipped on {@code ecommerce-service}'s side (Product Tags feature) without a matching route
 * added here, so it silently 404'd through Spring's static-resource handler (a
 * {@code NoResourceFoundException}, not an auth or 5xx error) until caught. Adding a new
 * {@code @RequestMapping} in any of the six standalone services is not by itself enough — always
 * add the matching {@code route(path(...))} line here in the same change.
 *
 * <p><b>{@code content-service}'s {@code /internal/content-items/**} is deliberately not routed
 * here</b> — it's service-to-service traffic ({@code ai-service} calls it directly on
 * {@code content-service}'s own port, gated by a shared {@code X-Internal-Api-Key} header, not a
 * JWT) and was never meant for external client traffic through this gateway.
 *
 * <p><b>{@code ai-service}'s {@code /api/v1/chat/stream} (the SSE streaming chat response) is
 * deliberately not routed here either — but it's still proxied by this app, just not through
 * this class.</b> Spring Cloud Gateway Server MVC's {@code http()} handler has documented problems
 * proxying Server-Sent Events (connection leaks, broken chunked streaming — see the upstream
 * project's issue tracker), so this one path is relayed by {@link ChatStreamProxyController}
 * instead, a purpose-built streaming proxy using the JDK's own {@code HttpClient} rather than
 * Gateway Server MVC's DSL. Only {@code /api/v1/chat/sessions/**} (plain REST — session
 * listing/history) is routed through this class; {@code /stream} is the one path
 * {@code ChatStreamProxyController} owns. The GUI never calls {@code ai-service} directly for
 * anything anymore — see that class's own Javadoc for why, and why {@code ai-service}'s own
 * {@code CorsConfig} was deleted outright (not just narrowed) once this landed.
 */
@Configuration
@RequiredArgsConstructor
public class GatewayRoutesConfig {

    private final GatewayServicesProperties services;

    /** Routes {@code /api/v1/admin/products/**}, {@code /api/v1/admin/product-categories/**},
     * {@code /api/v1/admin/product-tags/**}, {@code /api/v1/admin/product-attributes/**},
     * {@code /api/v1/admin/orders/**}, {@code /api/v1/admin/coupons/**},
     * {@code /api/v1/public/products/**}, {@code /api/v1/public/product-categories/**},
     * {@code /api/v1/public/payment-config}, {@code /api/v1/cart/**}, {@code /api/v1/checkout/**},
     * {@code /api/v1/orders/**}, {@code /api/v1/addresses/**}, and {@code /api/v1/coupons/**} to
     * {@code ecommerce-service}. */
    @Bean
    public RouterFunction<ServerResponse> ecommerceServiceRoutes() {
        String baseUrl = services.getEcommerceServiceBaseUrl();
        return route("ecommerce-service")
                .route(path("/api/v1/admin/products/**"), http(baseUrl))
                .route(path("/api/v1/admin/product-categories/**"), http(baseUrl))
                .route(path("/api/v1/admin/product-tags/**"), http(baseUrl))
                .route(path("/api/v1/admin/product-attributes/**"), http(baseUrl))
                .route(path("/api/v1/admin/orders/**"), http(baseUrl))
                .route(path("/api/v1/admin/coupons/**"), http(baseUrl))
                .route(path("/api/v1/public/products/**"), http(baseUrl))
                .route(path("/api/v1/public/product-categories/**"), http(baseUrl))
                .route(path("/api/v1/public/payment-config"), http(baseUrl))
                .route(path("/api/v1/cart/**"), http(baseUrl))
                .route(path("/api/v1/checkout/**"), http(baseUrl))
                .route(path("/api/v1/orders/**"), http(baseUrl))
                .route(path("/api/v1/addresses/**"), http(baseUrl))
                .route(path("/api/v1/coupons/**"), http(baseUrl))
                .build();
    }

    /** Routes {@code /api/v1/auth/**} and {@code /api/v1/users/me/**} to {@code identity-service}. */
    @Bean
    public RouterFunction<ServerResponse> identityServiceRoutes() {
        String baseUrl = services.getIdentityServiceBaseUrl();
        return route("identity-service")
                .route(path("/api/v1/auth/**"), http(baseUrl))
                .route(path("/api/v1/users/me/**"), http(baseUrl))
                .build();
    }

    /** Routes {@code /api/v1/projects/**} and {@code /api/v1/tasks/**} to {@code task-service}. */
    @Bean
    public RouterFunction<ServerResponse> taskServiceRoutes() {
        String baseUrl = services.getTaskServiceBaseUrl();
        return route("task-service")
                .route(path("/api/v1/projects/**"), http(baseUrl))
                .route(path("/api/v1/tasks/**"), http(baseUrl))
                .build();
    }

    /** Routes DMs, friends, groups/channels, and the "other users" half of {@code /api/v1/users/**} to {@code social-service}. */
    @Bean
    public RouterFunction<ServerResponse> socialServiceRoutes() {
        String baseUrl = services.getSocialServiceBaseUrl();
        return route("social-service")
                .route(path("/api/v1/dms/**"), http(baseUrl))
                .route(path("/api/v1/friends/**"), http(baseUrl))
                .route(path("/api/v1/groups/**"), http(baseUrl))
                .route(path("/api/v1/channels/**"), http(baseUrl))
                .route(path("/api/v1/users/public/**"), http(baseUrl))
                .route(path("/api/v1/users/search"), http(baseUrl))
                .build();
    }

    /** Routes admin content CRUD and the "published content" half of {@code /api/v1/public/**} to {@code content-service}. */
    @Bean
    public RouterFunction<ServerResponse> contentServiceRoutes() {
        String baseUrl = services.getContentServiceBaseUrl();
        return route("content-service")
                .route(path("/api/v1/admin/articles/**"), http(baseUrl))
                .route(path("/api/v1/admin/categories/**"), http(baseUrl))
                .route(path("/api/v1/admin/question-answers/**"), http(baseUrl))
                .route(path("/api/v1/admin/tags/**"), http(baseUrl))
                .route(path("/api/v1/public/question-answers/**"), http(baseUrl))
                .route(path("/api/v1/public/articles/**"), http(baseUrl))
                .build();
    }

    /**
     * Routes chat session listing/history and admin indexing/embeddings/pipeline-metrics to
     * {@code ai-service}. Deliberately excludes {@code /api/v1/chat/stream} — see class Javadoc.
     */
    @Bean
    public RouterFunction<ServerResponse> aiServiceRoutes() {
        String baseUrl = services.getAiServiceBaseUrl();
        return route("ai-service")
                .route(path("/api/v1/chat/sessions/**"), http(baseUrl))
                .route(path("/api/v1/admin/embeddings/**"), http(baseUrl))
                .route(path("/api/v1/admin/indexing/**"), http(baseUrl))
                .route(path("/api/v1/admin/pipeline-metrics/**"), http(baseUrl))
                .build();
    }
}
