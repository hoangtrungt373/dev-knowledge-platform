package com.ttg.devknowledgeplatform.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.stomp.StompSession;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.identity.security.jwt.AccessTokenClaims;
import com.ttg.devknowledgeplatform.social.dto.messaging.DmMessageResponse;
import com.ttg.devknowledgeplatform.social.dto.messaging.SendMessageRequest;
import com.ttg.devknowledgeplatform.social.dto.messaging.WsErrorResponse;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Integration test for {@code DmMessagingController}, run against the real STOMP broker assembled
 * by {@code gateway}'s {@code WebSocketConfig}/{@code StompAuthChannelInterceptor} — see
 * {@link AbstractStompIntegrationTest}'s Javadoc for why the full context is required.
 */
class DmMessagingStompIntegrationTest extends AbstractStompIntegrationTest {

    private static final String DM_QUEUE = "/user/queue/dms";
    private static final String ERROR_QUEUE = "/user/queue/errors";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Test
    void sendMessage_deliversToBothParticipants_whenFriends() throws Exception {
        User sender = persistUser();
        User recipient = persistUser();
        makeFriends(sender, recipient);

        StompSession senderSession = connect(accessTokenFor(sender));
        StompSession recipientSession = connect(accessTokenFor(recipient));
        BlockingQueue<DmMessageResponse> senderQueue = subscribeQueue(senderSession, DM_QUEUE, DmMessageResponse.class);
        BlockingQueue<DmMessageResponse> recipientQueue = subscribeQueue(recipientSession, DM_QUEUE, DmMessageResponse.class);
        awaitSubscription();

        senderSession.send("/app/dms/" + recipient.getUserUuid() + "/messages", new SendMessageRequest("Hello there", null));

        DmMessageResponse deliveredToSender = senderQueue.poll(5, TimeUnit.SECONDS);
        DmMessageResponse deliveredToRecipient = recipientQueue.poll(5, TimeUnit.SECONDS);

        assertThat(deliveredToSender).isNotNull();
        assertThat(deliveredToSender.content()).isEqualTo("Hello there");
        assertThat(deliveredToRecipient).isNotNull();
        assertThat(deliveredToRecipient.content()).isEqualTo("Hello there");
        assertThat(deliveredToSender.threadId()).isEqualTo(deliveredToRecipient.threadId());
    }

    @Test
    void secondMessageOnSameThread_isDeliveredWithoutError() throws Exception {
        // Regression guard for the OSIV note on DmMessagingController: the existing-thread branch
        // loads a genuine lazy DmThread proxy inside DmServiceImpl's transaction, and STOMP handling
        // (unlike REST, which rides Open-Session-In-View) has no open Hibernate session by the time
        // the controller runs. If that lazy association were ever touched there, this second send
        // would silently fail to arrive (a LazyInitializationException isn't an ApiException, so it
        // never becomes a WsErrorResponse) rather than throw somewhere this test can catch directly.
        User sender = persistUser();
        User recipient = persistUser();
        makeFriends(sender, recipient);

        StompSession senderSession = connect(accessTokenFor(sender));
        BlockingQueue<DmMessageResponse> senderQueue = subscribeQueue(senderSession, DM_QUEUE, DmMessageResponse.class);
        awaitSubscription();

        senderSession.send("/app/dms/" + recipient.getUserUuid() + "/messages", new SendMessageRequest("first", null));
        DmMessageResponse first = senderQueue.poll(5, TimeUnit.SECONDS);
        assertThat(first).isNotNull();

        senderSession.send("/app/dms/" + recipient.getUserUuid() + "/messages", new SendMessageRequest("second", null));
        DmMessageResponse second = senderQueue.poll(5, TimeUnit.SECONDS);

        assertThat(second).isNotNull();
        assertThat(second.content()).isEqualTo("second");
        assertThat(second.threadId()).isEqualTo(first.threadId());
    }

    @Test
    void connect_rejected_whenAuthorizationHeaderMissing() {
        assertThrows(Exception.class, () -> connect(null));
    }

    @Test
    void connect_rejected_whenTokenExpired() {
        User user = persistUser();
        String expiredToken = buildExpiredAccessToken(user);

        assertThrows(Exception.class, () -> connect(expiredToken));
    }

    @Test
    void connect_rejected_whenRefreshTokenPresentedInsteadOfAccessToken() {
        User user = persistUser();
        String refreshToken = refreshTokenFor(user);

        assertThrows(Exception.class, () -> connect(refreshToken));
    }

    @Test
    void sendMessage_producesWsErrorResponse_whenNotFriends() throws Exception {
        User sender = persistUser();
        User stranger = persistUser();
        // deliberately no makeFriends(...) call

        StompSession senderSession = connect(accessTokenFor(sender));
        BlockingQueue<WsErrorResponse> errorQueue = subscribeQueue(senderSession, ERROR_QUEUE, WsErrorResponse.class);
        BlockingQueue<DmMessageResponse> dmQueue = subscribeQueue(senderSession, DM_QUEUE, DmMessageResponse.class);
        awaitSubscription();

        senderSession.send("/app/dms/" + stranger.getUserUuid() + "/messages", new SendMessageRequest("hi", null));

        WsErrorResponse error = errorQueue.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.errorCode()).isEqualTo("DM_001");
        assertThat(dmQueue.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void sendMessage_producesWsErrorResponse_whenRecipientUuidUnknown() throws Exception {
        User sender = persistUser();

        StompSession senderSession = connect(accessTokenFor(sender));
        BlockingQueue<WsErrorResponse> errorQueue = subscribeQueue(senderSession, ERROR_QUEUE, WsErrorResponse.class);
        awaitSubscription();

        senderSession.send("/app/dms/" + UUID.randomUUID() + "/messages", new SendMessageRequest("hi", null));

        WsErrorResponse error = errorQueue.poll(5, TimeUnit.SECONDS);
        assertThat(error).isNotNull();
        assertThat(error.errorCode()).isEqualTo("USER_001");
    }

    /** Manually signs a token whose expiry is already in the past — {@code JwtTokenProvider}'s own
     *  {@code generateToken} always uses the live {@code jwt.expiration} property, so it can't produce
     *  an already-expired token; this reuses {@link AccessTokenClaims}'s claim shape instead of
     *  duplicating it. */
    private String buildExpiredAccessToken(User user) {
        AccessTokenClaims claims = AccessTokenClaims.from(user);
        Instant now = Instant.now();
        return Jwts.builder()
                .claims(claims.toClaimsMap())
                .subject(user.getEmail())
                .issuedAt(Date.from(now.minusSeconds(600)))
                .expiration(Date.from(now.minusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), Jwts.SIG.HS512)
                .compact();
    }

    /** STOMP SUBSCRIBE is fire-and-forget from the client's side; give the broker a moment to
     *  register it before sending, so the very first message isn't lost to a subscribe/send race. */
    private void awaitSubscription() throws InterruptedException {
        Thread.sleep(200);
    }
}
