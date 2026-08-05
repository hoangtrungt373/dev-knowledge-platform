package com.ttg.devknowledgeplatform.ws;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.UserProvider;
import com.ttg.devknowledgeplatform.common.enums.UserRole;
import com.ttg.devknowledgeplatform.common.enums.UserStatus;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.social.entity.FriendRequest;
import com.ttg.devknowledgeplatform.social.service.FriendService;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import jakarta.ws.rs.core.Response;

/**
 * Base class for STOMP integration tests against the real WebSocket stack.
 *
 * <p>Boots the full {@code gateway} application context (not a slice) because
 * {@code WebSocketConfig}/{@code StompAuthChannelInterceptor} — the classes that actually wire
 * {@code social-service}'s {@code DmMessagingController} into a running STOMP broker — only ever
 * get assembled together here; {@code social-service} itself has no {@code @SpringBootApplication}.
 * Postgres, Redis, MinIO, and Keycloak are all real Testcontainers instances (rather than mocking
 * the Redis-cache/MinIO-storage/JWT-verification beans this context also creates) so a passing
 * test means the whole wiring genuinely works, not just the DM-specific slice of it.
 *
 * <p>The Keycloak realm imported here ({@code keycloak/test-realm-export.json}) is a separate,
 * minimal realm from the checked-in dev one (`docker/keycloak/realm-export.json`) — just enough
 * roles/clients for tests to fetch real tokens via the Resource Owner Password grant, which the
 * real {@code gui} client deliberately disables.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractStompIntegrationTest {

    private static final String TEST_REALM = "dev-knowledge-platform-test";
    private static final String TEST_CLIENT_ID = "test-client";
    private static final String TEST_CLIENT_SHORT_LIVED_ID = "test-client-short-lived";
    private static final String TEST_PASSWORD = "test-password";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .waitingFor(Wait.forListeningPort());

    @Container
    static final GenericContainer<?> MINIO =
            new GenericContainer<>(DockerImageName.parse("minio/minio:latest"))
                    .withExposedPorts(9000)
                    .withEnv("MINIO_ROOT_USER", "minioadmin")
                    .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
                    .withCommand("server", "/data")
                    .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    @Container
    static final KeycloakContainer KEYCLOAK =
            new KeycloakContainer("quay.io/keycloak/keycloak:26.0")
                    .withRealmImportFile("keycloak/test-realm-export.json");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("app.storage.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));

        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> KEYCLOAK.getAuthServerUrl() + "/realms/" + TEST_REALM);
    }

    @LocalServerPort
    private int port;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected FriendService friendService;

    @Autowired
    protected ObjectMapper objectMapper;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUpStompClient() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter() {
            {
                setObjectMapper(objectMapper);
            }
        });
    }

    /**
     * Persists a distinct {@link User} row backed by a matching Keycloak user (created via the
     * admin client), linked via {@code keycloakSubjectId} — so {@link #accessTokenFor} mints a
     * real token whose {@code sub} this row already matches, exercising
     * {@code KeycloakJwtAuthenticationConverter}'s find-path (the realistic production path) on
     * every STOMP CONNECT, not its JIT-create-path.
     */
    protected User persistUser() {
        String suffix = UUID.randomUUID().toString();
        String email = "user-" + suffix + "@test.local";
        String keycloakSubjectId = createKeycloakUser(email);

        User user = User.builder()
                .userUuid(suffix)
                .email(email)
                .username("user-" + suffix)
                .password("not-used-in-these-tests")
                .provider(UserProvider.LOCAL)
                .role(UserRole.USER)
                .status(UserStatus.OFFLINE)
                .emailVerified(true)
                .enabled(true)
                .keycloakSubjectId(keycloakSubjectId)
                .build();
        return userRepository.save(user);
    }

    private static String createKeycloakUser(String email) {
        UserRepresentation kcUser = new UserRepresentation();
        kcUser.setUsername(email);
        kcUser.setEmail(email);
        kcUser.setEnabled(true);
        kcUser.setEmailVerified(true);

        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(TEST_PASSWORD);
        credential.setTemporary(false);
        kcUser.setCredentials(List.of(credential));

        Keycloak admin = KEYCLOAK.getKeycloakAdminClient();
        Response response = admin.realm(TEST_REALM).users().create(kcUser);
        try {
            return CreatedResponseUtil.getCreatedId(response);
        } finally {
            response.close();
        }
    }

    /** Establishes an accepted friendship between two users via the real service, not a repository shortcut. */
    protected void makeFriends(User a, User b) {
        FriendRequest request = friendService.sendRequest(a.getId(), b.getUserUuid());
        friendService.acceptRequest(request.getId(), b.getId());
    }

    protected String accessTokenFor(User user) {
        return fetchToken(TEST_CLIENT_ID, user.getEmail(), "access_token");
    }

    protected String refreshTokenFor(User user) {
        return fetchToken(TEST_CLIENT_ID, user.getEmail(), "refresh_token");
    }

    /**
     * Fetches a real access token whose expiry is already in the past — via
     * {@code test-client-short-lived}'s 1-second access-token lifespan (set in
     * {@code keycloak/test-realm-export.json}), rather than hand-signing a token: this app no
     * longer holds a private key to sign with (Keycloak does), so a genuinely-expired real token
     * is the only way to test rejection now.
     */
    protected String buildExpiredAccessToken(User user) {
        String token = fetchToken(TEST_CLIENT_SHORT_LIVED_ID, user.getEmail(), "access_token");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test token to expire", e);
        }
        return token;
    }

    private String fetchToken(String clientId, String email, String tokenField) {
        try {
            String body = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&grant_type=password"
                    + "&username=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(TEST_PASSWORD, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(KEYCLOAK.getAuthServerUrl() + "/realms/" + TEST_REALM + "/protocol/openid-connect/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> tokenResponse = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            Object token = tokenResponse.get(tokenField);
            if (token == null) {
                throw new IllegalStateException("Token endpoint response had no " + tokenField + ": " + response.body());
            }
            return (String) token;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch test token from Keycloak", e);
        }
    }

    /**
     * Connects and completes the STOMP CONNECT frame carrying {@code Authorization: Bearer <token>}
     * as a native STOMP header (not an HTTP handshake header) — matching how
     * {@code StompAuthChannelInterceptor} actually reads it. Pass {@code null} to omit the header.
     */
    protected StompSession connect(String bearerToken) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (bearerToken != null) {
            connectHeaders.add("Authorization", "Bearer " + bearerToken);
        }
        return stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
    }

    /** Subscribes and collects every payload delivered to {@code destination} into a queue for polling. */
    protected <T> BlockingQueue<T> subscribeQueue(StompSession session, String destination, Class<T> payloadType) {
        BlockingQueue<T> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return payloadType;
            }

            @SuppressWarnings("unchecked")
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((T) payload);
            }
        });
        return queue;
    }
}
