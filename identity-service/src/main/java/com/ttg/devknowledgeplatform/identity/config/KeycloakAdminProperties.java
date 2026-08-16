package com.ttg.devknowledgeplatform.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Connection details for Keycloak's Admin REST API, used by {@code KeycloakAdminService} to
 * create user accounts on registration. Backed by the {@code identity-service-admin} confidential
 * client (service account, {@code manage-users} role) — see {@code docker/keycloak/realm-export.json}.
 */
@Data
@ConfigurationProperties(prefix = "app.keycloak-admin")
public class KeycloakAdminProperties {

    /** Keycloak's own base URL, e.g. {@code http://localhost:8180} / {@code http://keycloak:8080}. */
    private String serverUrl;

    /** The realm to manage users in. */
    private String realm;

    /** The confidential client id this service authenticates as (client_credentials grant). */
    private String clientId;

    /** The above client's secret — never logged, never sent to any frontend. */
    private String clientSecret;
}
