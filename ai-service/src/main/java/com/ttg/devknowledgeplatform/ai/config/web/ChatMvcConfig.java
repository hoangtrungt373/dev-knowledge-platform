package com.ttg.devknowledgeplatform.ai.config.web;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * This module's own Spring MVC configuration, composed automatically alongside {@code gateway}'s
 * {@code WebMvcConfig} — Spring merges every {@link WebMvcConfigurer} bean in the context, so this
 * module doesn't need {@code gateway} to register interceptors on its behalf.
 *
 * <p>Registers the chat rate-limit interceptor for all {@code /api/v1/chat/**} paths. The
 * interceptor itself skips non-POST requests so GET session endpoints are unaffected.
 */
@Configuration
@RequiredArgsConstructor
public class ChatMvcConfig implements WebMvcConfigurer {

    private final ChatRateLimitInterceptor chatRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(chatRateLimitInterceptor)
                .addPathPatterns("/api/v1/chat/**");
    }
}
