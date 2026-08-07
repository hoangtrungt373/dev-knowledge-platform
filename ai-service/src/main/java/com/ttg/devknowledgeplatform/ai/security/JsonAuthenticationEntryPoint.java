package com.ttg.devknowledgeplatform.ai.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.dto.ErrorResponse;
import com.ttg.devknowledgeplatform.common.exception.CommonErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Returns a JSON {@code 401 Unauthorized} error body instead of the default Spring Security
 * redirect or HTML response when an unauthenticated request reaches a protected endpoint.
 *
 * <p>Duplicated from {@code gateway}'s class of the same name — a generic Spring Security
 * component with no {@code gateway}-specific dependency. Registering this bean in
 * {@code SecurityConfig.exceptionHandling()} ensures all authentication failures produce
 * a machine-readable {@link com.ttg.devknowledgeplatform.common.dto.ErrorResponse} payload.
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = ErrorResponse.builder()
                .errorCode(CommonErrorCode.AUTH_UNAUTHORIZED.getCode())
                .status(HttpStatus.UNAUTHORIZED.value())
                .errorMessage("Authentication required")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
