package com.ttg.devknowledgeplatform.service;

import java.util.Collections;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.UserProvider;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.dto.OAuth2UserInfo;
import com.ttg.devknowledgeplatform.dto.OAuth2UserInfoFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    
    private final UserService userService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        log.info("Loading OAuth2 user for registration: {}", userRequest.getClientRegistration().getRegistrationId());

        OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, oAuth2User.getAttributes());
        
        if (userInfo.getEmail() == null || userInfo.getEmail().isEmpty()) {
            log.error("Email not found from OAuth2 provider: {}", registrationId);
            throw new OAuth2AuthenticationException("Email not found from OAuth2 provider");
        }
        
        UserProvider provider = getProvider(registrationId);
        
        User user = userService.findByProviderAndProviderId(provider, userInfo.getId());
        
        if (user == null) {
            user = userService.findByEmail(userInfo.getEmail());
            if (user != null) {
                user = updateExistingUserWithOAuth2(user, userInfo, provider);
            } else {
                user = userService.registerOAuth2User(userInfo, provider);
            }
        } else {
            user = userService.updateOAuth2User(user, userInfo);
        }
        
        List<GrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        
        return CustomOAuth2User.builder()
                .id(user.getUserUuid())
                .email(user.getEmail())
                .name(user.getUsername())
                .attributes(oAuth2User.getAttributes())
                .authorities(authorities)
                .build();
    }
    
    private UserProvider getProvider(String registrationId) {
        switch (registrationId.toLowerCase()) {
            case "google":
                return UserProvider.GOOGLE;
            case "facebook":
                return UserProvider.FACEBOOK;
            default:
                throw new OAuth2AuthenticationException("Unsupported OAuth2 provider: " + registrationId);
        }
    }
    
    private User updateExistingUserWithOAuth2(User existingUser, OAuth2UserInfo userInfo, UserProvider provider) {
        log.info("Updating existing user with OAuth2 provider info: {} (USER_ID: {})", 
                existingUser.getEmail(), existingUser.getId());
        
        existingUser.setProvider(provider);
        existingUser.setProviderId(userInfo.getId());
        existingUser.setProfilePicture(userInfo.getImageUrl());
        existingUser.setEmailVerified(true);
        existingUser.setUsrLastModification("system");  // Update audit field
        
        return userService.updateOAuth2User(existingUser, userInfo);
    }
}
