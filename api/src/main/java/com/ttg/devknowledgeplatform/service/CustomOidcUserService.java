package com.ttg.devknowledgeplatform.service;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.UserProvider;
import com.ttg.devknowledgeplatform.dto.OAuth2UserInfo;
import com.ttg.devknowledgeplatform.dto.OAuth2UserInfoFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class CustomOidcUserService extends OidcUserService {

    private final UserService userService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        log.info("Loading OIDC user for registration: {}", registrationId);

        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.getOAuth2UserInfo(registrationId, oidcUser.getAttributes());
        if (userInfo.getEmail() == null || userInfo.getEmail().isEmpty()) {
            log.error("Email not found from OIDC provider: {}", registrationId);
            throw new OAuth2AuthenticationException("Email not found from OIDC provider");
        }

        UserProvider provider = getProvider(registrationId);

        User user = userService.findByProviderAndProviderId(provider, userInfo.getId());
        if (user == null) {
            user = userService.findByEmail(userInfo.getEmail());
            if (user != null) {
                user = linkExistingUser(user, userInfo, provider);
            } else {
                user = userService.registerOAuth2User(userInfo, provider);
            }
        } else {
            user = userService.updateOAuth2User(user, userInfo);
        }

        log.info("OIDC user processed: {} (USER_ID: {}, USER_UUID: {})",
                user.getEmail(), user.getId(), user.getUserUuid());

        return oidcUser;
    }

    private UserProvider getProvider(String registrationId) {
        return switch (registrationId.toLowerCase()) {
            case "google" -> UserProvider.GOOGLE;
            case "facebook" -> UserProvider.FACEBOOK;
            default -> throw new OAuth2AuthenticationException("Unsupported OAuth2 provider: " + registrationId);
        };
    }

    private User linkExistingUser(User existingUser, OAuth2UserInfo userInfo, UserProvider provider) {
        log.info("Linking existing user to provider: {} (USER_ID: {})",
                existingUser.getEmail(), existingUser.getId());

        existingUser.setProvider(provider);
        existingUser.setProviderId(userInfo.getId());
        existingUser.setProfilePicture(userInfo.getImageUrl());
        existingUser.setEmailVerified(true);
        existingUser.setUsrLastModification("system");

        return userService.updateOAuth2User(existingUser, userInfo);
    }
}

