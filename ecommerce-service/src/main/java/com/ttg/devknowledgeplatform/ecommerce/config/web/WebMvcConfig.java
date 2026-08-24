package com.ttg.devknowledgeplatform.ecommerce.config.web;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.RequiredArgsConstructor;

import com.ttg.devknowledgeplatform.infra.security.CurrentUserIdArgumentResolver;

/**
 * This module's own Spring MVC configuration — registers {@code infra}'s shared
 * {@link CurrentUserIdArgumentResolver} (see that class's own Javadoc for why it's shared now,
 * not a local copy) so any controller parameter annotated with
 * {@link com.ttg.devknowledgeplatform.common.annotation.CurrentUserId} resolves automatically.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdArgumentResolver);
    }
}
