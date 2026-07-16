package com.ttg.devknowledgeplatform.ws;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.UserProvider;
import com.ttg.devknowledgeplatform.common.enums.UserRole;
import com.ttg.devknowledgeplatform.common.enums.UserStatus;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.identity.security.JwtTokenProvider;
import com.ttg.devknowledgeplatform.social.entity.FriendRequest;
import com.ttg.devknowledgeplatform.social.service.FriendService;

/**
 * Base class for STOMP integration tests against the real WebSocket stack.
 *
 * <p>Boots the full {@code gateway} application context (not a slice) because
 * {@code WebSocketConfig}/{@code StompAuthChannelInterceptor} — the classes that actually wire
 * {@code social-service}'s {@code DmMessagingController} into a running STOMP broker — only ever
 * get assembled together here; {@code social-service} itself has no {@code @SpringBootApplication}.
 * Postgres, Redis, and MinIO are all real Testcontainers instances (rather than mocking the
 * Redis-cache/MinIO-storage beans this context also creates) so a passing test means the whole
 * wiring genuinely works, not just the DM-specific slice of it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractStompIntegrationTest {

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

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("app.storage.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
    }

    @LocalServerPort
    private int port;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected FriendService friendService;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

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

    /** Persists a distinct {@link User} row, ready to log in as (OAuth2-style, no real password login). */
    protected User persistUser() {
        String suffix = UUID.randomUUID().toString();
        User user = User.builder()
                .userUuid(suffix)
                .email("user-" + suffix + "@test.local")
                .username("user-" + suffix)
                .password("not-used-in-these-tests")
                .provider(UserProvider.LOCAL)
                .role(UserRole.USER)
                .status(UserStatus.OFFLINE)
                .emailVerified(true)
                .enabled(true)
                .build();
        return userRepository.save(user);
    }

    /** Establishes an accepted friendship between two users via the real service, not a repository shortcut. */
    protected void makeFriends(User a, User b) {
        FriendRequest request = friendService.sendRequest(a.getId(), b.getUserUuid());
        friendService.acceptRequest(request.getId(), b.getId());
    }

    protected String accessTokenFor(User user) {
        return jwtTokenProvider.generateToken(user);
    }

    protected String refreshTokenFor(User user) {
        return jwtTokenProvider.generateRefreshToken(user);
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
