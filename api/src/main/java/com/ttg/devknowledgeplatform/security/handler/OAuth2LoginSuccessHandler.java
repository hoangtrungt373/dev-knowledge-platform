package com.ttg.devknowledgeplatform.security.handler;

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
import com.ttg.devknowledgeplatform.security.service.StateTokenService;
import com.ttg.devknowledgeplatform.security.service.UserService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles a successful OAuth2 or OIDC login by issuing JWTs and redirecting the browser
 * back to the frontend via a short-lived state token.
 *
 * <p>Direct token delivery in the redirect URL would expose the tokens in browser history
 * and server logs. Instead, this handler:
 * <ol>
 *   <li>Generates an access token and a refresh token for the authenticated user.</li>
 *   <li>Stores both tokens (along with basic profile data) in Redis under a one-time
 *       state token with a short TTL (configured via {@code app.state.token.expiration}).</li>
 *   <li>Redirects the browser to {@code {frontendUrl}/auth/callback?state=<stateToken>}.</li>
 * </ol>
 *
 * <p>The frontend then calls {@code POST /api/v1/auth/exchange-state} with the state token
 * to retrieve the actual JWTs, at which point the Redis entry is deleted.
 */
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

