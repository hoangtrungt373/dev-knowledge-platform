package com.ttg.devknowledgeplatform.identity.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

/**
 * Produces the {@link Keycloak} admin-client bean {@code KeycloakAdminService} uses to call
 * Keycloak's Admin REST API. Authenticates as the {@code identity-service-admin} confidential
 * client via the client_credentials grant — never a user's own credentials.
 */
@Configuration
@RequiredArgsConstructor
public class KeycloakAdminConfig {

    private final KeycloakAdminProperties properties;

    /**
     * @return a {@link Keycloak} client authenticated via client_credentials, ready to call the
     *         Admin REST API against this app's configured realm.
     */
    @Bean
    public Keycloak keycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(properties.getServerUrl())
                .realm(properties.getRealm())
                .clientId(properties.getClientId())
                .clientSecret(properties.getClientSecret())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .build();
    }
}
