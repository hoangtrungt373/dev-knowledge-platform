package com.ttg.devknowledgeplatform.content.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.dto.ErrorResponse;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;
import com.ttg.devknowledgeplatform.content.config.InternalApiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Gates every {@code /internal/**} request behind a shared-secret header instead of an end-user
 * JWT — {@code ai-service} is the only intended caller, and it has no end-user principal to attach
 * to a server-to-server indexing call.
 *
 * <p><strong>Option A (chosen): shared API key header.</strong> Both apps are configured with the
 * same {@code app.internal-api.key} (env var {@code INTERNAL_API_KEY}); this filter rejects any
 * {@code /internal/**} request whose {@code X-Internal-Api-Key} header doesn't match. Simple, no
 * extra moving parts, matches the trust model of a same-Docker-network call.
 * <p><strong>Option B (alternative): OAuth2 client-credentials grant against Keycloak</strong>,
 * treating {@code ai-service} as a confidential client and validating a service-issued JWT the same
 * way end-user tokens are validated. More consistent with the rest of this codebase's security
 * model and auditable via Keycloak's own client management, but is real infrastructure this
 * study-project scope doesn't need yet (a second client registration, a token-fetch step
 * {@code ai-service}'s HTTP client would need before every call or cache). Revisit if a third
 * service ever needs to call an internal endpoint, or if these endpoints are ever exposed outside
 * the Docker network.
 *
 * <p>Registered as a plain {@link OncePerRequestFilter} bean (not part of the Spring Security
 * filter chain) — {@code SecurityConfig} marks {@code /internal/**} {@code permitAll()} so Spring
 * Security's own filter lets the request through unauthenticated, and this filter runs immediately
 * after to enforce the API key. This keeps the internal-API concern fully owned by this module —
 * once it's extracted into a standalone service, this filter (and its property) move with it
 * unchanged, and the owning app configures its own resource-server security however it needs to
 * around it.
 */
@Component
@RequiredArgsConstructor
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/internal/";
    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final InternalApiProperties internalApiProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey == null || !providedKey.equals(internalApiProperties.getKey())) {
            rejectRequest(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void rejectRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = ErrorResponse.builder()
                .errorCode(CommonErrorCode.AUTH_FORBIDDEN.getCode())
                .status(HttpStatus.FORBIDDEN.value())
                .errorMessage("Missing or invalid internal API key")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
