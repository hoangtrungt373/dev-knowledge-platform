package com.ttg.devknowledgeplatform.routing;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import lombok.RequiredArgsConstructor;

/**
 * Proxies {@code POST /api/v1/chat/stream} — {@code ai-service}'s SSE streaming chat response —
 * by hand, bypassing {@link GatewayRoutesConfig}'s usual Spring Cloud Gateway Server MVC
 * {@code RouterFunction} routing entirely for this one path.
 *
 * <p><b>Why this exists instead of one more line in {@code GatewayRoutesConfig}:</b> Gateway
 * Server MVC's {@code HandlerFunctions.http()} has real, documented upstream problems proxying
 * Server-Sent Events (connection leaks, broken chunked streaming). This class is a purpose-built
 * relay for just this one endpoint instead — read a chunk from {@code ai-service}, write it to the
 * browser, flush, repeat, so the token-by-token streaming UX genuinely streams rather than
 * buffering (or leaking a connection). Once this exists, the GUI never needs to call
 * {@code ai-service} directly for anything — {@code ai-service}'s own {@code CorsConfig} was
 * deleted outright once this landed, since nothing calls it cross-origin anymore.
 *
 * <p><b>Why the JDK's own {@link HttpClient}, not Spring's {@code RestClient}</b> (every other
 * outbound HTTP call in this reactor's standalone services uses {@code RestClient}, e.g.
 * {@code ai-service}'s own {@code ContentServiceClientImpl}): this proxy needs the upstream
 * response's status code available *before* committing to stream its body — {@code RestClient}'s
 * {@code exchange()} scopes the response (and its body {@code InputStream}) to one callback, which
 * doesn't fit "decide the response status now, stream the body later" the way this controller
 * needs to. {@code HttpClient.send(..., BodyHandlers.ofInputStream())} returns as soon as headers
 * arrive, exposing {@code statusCode()} and a lazily-readable body {@code InputStream} as two
 * independent fields on one object — exactly this shape.
 *
 * <p>Authorization is forwarded verbatim, never inspected here — {@code ai-service} verifies the
 * JWT itself regardless, same as every other backend this gateway proxies to (this app's own
 * {@code SecurityConfig} already requires authentication on this path before this method ever
 * runs, so the header is guaranteed present).
 *
 * <p>See {@link StreamingProxyAsyncConfig} for the matching async-dispatch/timeout wiring this
 * class depends on.
 */
@RestController
@RequiredArgsConstructor
public class ChatStreamProxyController {

    /**
     * Must match {@code ai-service}'s own {@code SseStreamTemplate.SSE_TIMEOUT_MS}. This proxy
     * relays that module's stream verbatim — a shorter timeout here would cut off a still-active
     * upstream response; a longer one would just wait past a stream {@code ai-service} has
     * already terminated on its own. Can't reference that constant directly (no Maven dependency
     * between the two modules), so the value is duplicated with this comment as the sync point.
     */
    private static final Duration UPSTREAM_TIMEOUT = Duration.ofMillis(60_000L);

    private static final int RELAY_BUFFER_SIZE = 1024;

    private final GatewayServicesProperties services;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @PostMapping("/api/v1/chat/stream")
    public ResponseEntity<StreamingResponseBody> streamChat(
            @RequestBody byte[] body,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
            @RequestHeader(HttpHeaders.ACCEPT) String accept
    ) throws IOException, InterruptedException {
        HttpRequest upstreamRequest = HttpRequest.newBuilder()
                .uri(URI.create(services.getAiServiceBaseUrl() + "/api/v1/chat/stream"))
                .timeout(UPSTREAM_TIMEOUT)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.ACCEPT, accept)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<InputStream> upstreamResponse =
                httpClient.send(upstreamRequest, HttpResponse.BodyHandlers.ofInputStream());

        StreamingResponseBody relay = outputStream -> {
            try (InputStream upstreamBody = upstreamResponse.body()) {
                byte[] buffer = new byte[RELAY_BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = upstreamBody.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                }
            }
        };

        return ResponseEntity.status(upstreamResponse.statusCode())
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(relay);
    }
}
