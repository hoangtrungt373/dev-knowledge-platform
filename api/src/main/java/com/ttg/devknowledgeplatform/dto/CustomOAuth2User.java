package com.ttg.devknowledgeplatform.dto;

import java.util.Collection;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CustomOAuth2User implements OAuth2User {
    private String id;
    private String email;
    private String name;
    private Map<String, Object> attributes;
    private Collection<? extends GrantedAuthority> authorities;
}
