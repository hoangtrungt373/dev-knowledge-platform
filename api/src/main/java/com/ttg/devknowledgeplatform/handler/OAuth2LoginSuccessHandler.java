package com.ttg.devknowledgeplatform.handler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.UserStatus;
import com.ttg.devknowledgeplatform.dto.CustomOAuth2User;
import com.ttg.devknowledgeplatform.security.JwtTokenProvider;
import com.ttg.devknowledgeplatform.service.StateTokenService;
import com.ttg.devknowledgeplatform.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final StateTokenService stateTokenService;
    
    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;
    
    @Value("${app.state.token.expiration:300}")
    private long stateTokenExpirationSeconds;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                      HttpServletResponse response, 
                                      Authentication authentication) throws IOException, ServletException {
        
        log.info("OAuth2 login successful for user: {}", authentication.getName());
        String email = extractEmail(authentication.getPrincipal());
        if (email == null || email.trim().isEmpty()) {
            log.error("Email not found in OAuth2/OIDC principal");
            response.sendRedirect(frontendUrl + "/login?error=email_not_found");
            return;
        }

        User user = userService.findByEmail(email);
        if (user == null) {
            log.error("User not found after OAuth2 authentication: {}", email);
            response.sendRedirect(frontendUrl + "/login?error=user_not_found");
            return;
        }
        
        userService.updateStatus(user.getId(), UserStatus.ONLINE);

        String accessToken = jwtTokenProvider.generateToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);
        
        log.info("Generated tokens for user: {} (USER_ID: {}, USER_UUID: {})", 
                user.getEmail(), user.getId(), user.getUserUuid());

        String stateToken = stateTokenService.generateStateToken();
        
        Map<String, String> tokenData = new HashMap<>();
        tokenData.put("accessToken", accessToken);
        tokenData.put("refreshToken", refreshToken);
        tokenData.put("userId", user.getUserUuid());
        tokenData.put("username", user.getUsername());
        tokenData.put("email", user.getEmail());
        tokenData.put("role", user.getRole().name());
        
        stateTokenService.storeTokenData(stateToken, tokenData, stateTokenExpirationSeconds);
        
        log.debug("Stored tokens with state token: {}", stateToken);
        
        response.sendRedirect(frontendUrl + "/auth/callback?state=" + stateToken);
    }
    private String extractEmail(Object principal) {
        if (principal instanceof CustomOAuth2User customOAuth2User) {
            return customOAuth2User.getEmail();
        }
        
        if (principal instanceof OidcUser oidcUser) {
            String email = oidcUser.getEmail();
            if (email == null || email.trim().isEmpty()) {
                email = oidcUser.getIdToken() != null ? oidcUser.getIdToken().getClaimAsString("email") : null;
            }
            if ((email == null || email.trim().isEmpty()) && oidcUser.getUserInfo() != null) {
                email = oidcUser.getUserInfo().getEmail();
            }
            return email;
        }
        
        if (principal instanceof OAuth2User oAuth2User) {
            Object emailObj = oAuth2User.getAttributes().get("email");
            return emailObj != null ? emailObj.toString() : null;
        }
        
        return null;
    }
}

