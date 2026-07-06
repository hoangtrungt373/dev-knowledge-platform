package com.ttg.devknowledgeplatform.social.service.seed;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.repository.UserRepository;
import com.ttg.devknowledgeplatform.social.entity.FriendRequest;
import com.ttg.devknowledgeplatform.social.entity.Friendship;
import com.ttg.devknowledgeplatform.social.enums.FriendRequestStatus;
import com.ttg.devknowledgeplatform.social.repository.FriendRequestRepository;
import com.ttg.devknowledgeplatform.social.repository.FriendshipRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds the friend graph from {@code data/csv/friend-requests.csv} (columns: requesterId,
 * addresseeId, status), referencing {@link User} by {@code seedId} (see {@code UserSeeder}) —
 * sample data for exercising the Friend Management GUI end to end.
 *
 * <p>Does <strong>not</strong> extend {@code content-service}'s {@code CsvSeeder<T>} Template
 * Method: a {@code status=ACCEPTED} row must persist both a {@link FriendRequest} <em>and</em> a
 * {@link Friendship}, which doesn't fit {@code CsvSeeder}'s one-entity-per-row
 * {@code buildEntity()}/{@code persist()} shape — the same reason {@code QuestionAnswerSeeder}
 * implements its own {@code seed()} rather than forcing a genuinely different shape through the
 * template.
 *
 * <p>When {@code status=ACCEPTED}, the resulting {@link Friendship} is created with the same
 * canonical ordering ({@code user1.id < user2.id}) that
 * {@code FriendServiceImpl.acceptRequest} uses in production, so seeded data satisfies the same
 * invariant real usage produces rather than inserting friendships no real flow could have created.
 *
 * <p>Idempotency: a row is skipped if a {@link FriendRequest} already exists between the two
 * referenced users, in either direction, regardless of status
 * ({@link FriendRequestRepository#existsBetween}). Unlike {@code Category}/{@code Tag}/
 * {@code User}, a request/friendship pair has no editable-field equivalent to
 * {@code NAME}/{@code EMAIL} that could invalidate this check, so neither {@link FriendRequest}
 * nor {@link Friendship} needs its own {@code seedId} (see Liquibase {@code DKP-0016}).
 *
 * <p>Requires {@code UserSeeder} (api) to have already run.
 *
 * @author ttg
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FriendGraphSeeder {

    private static final String CSV_LOCATION = "data/csv/friend-requests.csv";

    private final UserRepository userRepository;
    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;

    /**
     * Reads every row of {@code data/csv/friend-requests.csv} and inserts the ones whose pair
     * isn't already present.
     *
     * @return the number of rows inserted
     */
    public int seed() {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();

        int inserted = 0;
        int skipped = 0;
        try (InputStream in = new ClassPathResource(CSV_LOCATION).getInputStream();
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {

            for (CSVRecord record : parser) {
                User requester = resolveUser(record.get("requesterId"));
                User addressee = resolveUser(record.get("addresseeId"));
                FriendRequestStatus status = FriendRequestStatus.valueOf(record.get("status").trim().toUpperCase());

                if (friendRequestRepository.existsBetween(requester, addressee)) {
                    skipped++;
                    log.debug("FriendGraphSeeder: skipping existing pair {} <-> {}",
                            requester.getEmail(), addressee.getEmail());
                    continue;
                }

                FriendRequest friendRequest = FriendRequest.builder()
                        .requester(requester)
                        .addressee(addressee)
                        .status(status)
                        .build();
                friendRequestRepository.save(friendRequest);

                if (status == FriendRequestStatus.ACCEPTED) {
                    createFriendship(requester, addressee);
                }

                inserted++;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read seed CSV: " + CSV_LOCATION, e);
        }

        log.info("FriendGraphSeeder: inserted {} row(s), skipped {} already-present row(s)", inserted, skipped);
        return inserted;
    }

    private void createFriendship(User a, User b) {
        User user1 = a.getId() < b.getId() ? a : b;
        User user2 = a.getId() < b.getId() ? b : a;
        friendshipRepository.save(Friendship.builder().user1(user1).user2(user2).build());
    }

    private User resolveUser(String seedId) {
        return userRepository.findBySeedId(seedId)
                .orElseThrow(() -> new IllegalStateException(
                        "friend-requests.csv references unknown user id '" + seedId + "' — run UserSeeder first"));
    }
}
