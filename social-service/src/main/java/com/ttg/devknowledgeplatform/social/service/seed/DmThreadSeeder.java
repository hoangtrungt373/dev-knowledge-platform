package com.ttg.devknowledgeplatform.social.service.seed;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Component;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.social.entity.DmMessage;
import com.ttg.devknowledgeplatform.social.entity.DmThread;
import com.ttg.devknowledgeplatform.social.entity.Friendship;
import com.ttg.devknowledgeplatform.infra.service.seed.Seeder;
import com.ttg.devknowledgeplatform.social.enums.MessageType;
import com.ttg.devknowledgeplatform.social.repository.DmMessageRepository;
import com.ttg.devknowledgeplatform.social.repository.DmThreadRepository;
import com.ttg.devknowledgeplatform.social.repository.FriendshipRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds a random lorem-ipsum DM conversation for every existing {@link Friendship}, so the
 * Messages GUI (fronting {@code DmApi}/{@code DmMessagingApi}) has sample data to show — sample
 * data for exercising that GUI end to end, the same purpose {@link FriendGraphSeeder}/
 * {@code UserBlockSeeder} serve for the Friend Management GUI.
 *
 * <p>Does <strong>not</strong> extend {@code infra}'s {@code CsvSeeder<T>} Template Method: pairs
 * come from the already-seeded friend graph itself ({@link FriendshipRepository#findAll()}), not a
 * dedicated CSV — same reasoning {@link FriendGraphSeeder} documents for why it also implements its
 * own {@code seed()}.
 *
 * <p><strong>Backdating:</strong> a message's {@code dteCreation} (inherited audit column) is what
 * {@code DmMessageResponse.createdAt} maps to, but {@code AbstractEntity}'s {@code @PrePersist}
 * unconditionally resets it to "now" on {@code save()}, and the column is {@code updatable = false}
 * for any later JPA update. Each message is therefore saved normally first (to get its id), then
 * fixed up via {@link DmMessageRepository#backdateCreatedAt}, a JPQL bulk update that bypasses both
 * the listener and the {@code updatable} flag. {@link DmThread#getLastMessageAt()} has no such
 * restriction — it's set directly once all of a thread's messages are generated.
 *
 * <p>Requires {@code UserSeeder} and {@code FriendGraphSeeder} to have already run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DmThreadSeeder implements Seeder {

    private static final int MIN_MESSAGES = 5;
    private static final int MAX_MESSAGES = 15;
    private static final int MIN_SENTENCE_WORDS = 5;
    private static final int MAX_SENTENCE_WORDS = 15;
    private static final int SPREAD_DAYS = 14;

    private static final String[] LOREM_WORDS = {
            "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing", "elit", "sed",
            "do", "eiusmod", "tempor", "incididunt", "ut", "labore", "et", "dolore", "magna",
            "aliqua", "enim", "ad", "minim", "veniam", "quis", "nostrud", "exercitation", "ullamco",
            "laboris", "nisi", "aliquip", "ex", "ea", "commodo", "consequat", "duis", "aute",
            "irure", "in", "reprehenderit", "voluptate", "velit", "esse", "cillum", "eu", "fugiat",
            "nulla", "pariatur", "excepteur", "sint", "occaecat", "cupidatat", "non", "proident",
            "sunt", "culpa", "qui", "officia", "deserunt", "mollit", "anim", "id", "est", "laborum",
    };

    private final FriendshipRepository friendshipRepository;
    private final DmThreadRepository dmThreadRepository;
    private final DmMessageRepository dmMessageRepository;
    private final Random random = new Random();

    /**
     * Seeds one DM conversation per {@link Friendship} whose pair has no {@link DmThread} yet.
     *
     * @return the number of threads inserted
     */
    @Override
    public int seed() {
        List<Friendship> friendships = friendshipRepository.findAll();

        int inserted = 0;
        int skipped = 0;
        for (Friendship friendship : friendships) {
            User user1 = friendship.getUser1();
            User user2 = friendship.getUser2();

            if (dmThreadRepository.findByUser1AndUser2(user1, user2).isPresent()) {
                skipped++;
                continue;
            }

            seedThread(user1, user2);
            inserted++;
        }

        log.info("DmThreadSeeder: inserted {} thread(s), skipped {} already-present pair(s)", inserted, skipped);
        return inserted;
    }

    private void seedThread(User user1, User user2) {
        DmThread thread = dmThreadRepository.save(DmThread.builder().user1(user1).user2(user2).build());

        int messageCount = MIN_MESSAGES + random.nextInt(MAX_MESSAGES - MIN_MESSAGES + 1);
        List<Instant> timestamps = randomSpreadTimestamps(messageCount);

        for (Instant timestamp : timestamps) {
            User sender = random.nextBoolean() ? user1 : user2;
            DmMessage message = dmMessageRepository.save(DmMessage.builder()
                    .dmThread(thread)
                    .sender(sender)
                    .messageType(MessageType.TEXT)
                    .content(randomSentence())
                    .build());
            dmMessageRepository.backdateCreatedAt(message.getId(), timestamp);
        }

        thread.setLastMessageAt(timestamps.getLast());
        dmThreadRepository.save(thread);
    }

    /** {@code count} random instants within the last {@link #SPREAD_DAYS} days, ascending. */
    private List<Instant> randomSpreadTimestamps(int count) {
        Instant now = Instant.now();
        long spreadSeconds = ChronoUnit.SECONDS.between(now.minus(SPREAD_DAYS, ChronoUnit.DAYS), now);

        List<Instant> timestamps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long offsetSeconds = (long) (random.nextDouble() * spreadSeconds);
            timestamps.add(now.minusSeconds(offsetSeconds));
        }
        timestamps.sort(Comparator.naturalOrder());
        return timestamps;
    }

    private String randomSentence() {
        int wordCount = MIN_SENTENCE_WORDS + random.nextInt(MAX_SENTENCE_WORDS - MIN_SENTENCE_WORDS + 1);
        StringBuilder sentence = new StringBuilder();
        for (int i = 0; i < wordCount; i++) {
            if (i > 0) {
                sentence.append(' ');
            }
            sentence.append(LOREM_WORDS[random.nextInt(LOREM_WORDS.length)]);
        }
        sentence.setCharAt(0, Character.toUpperCase(sentence.charAt(0)));
        sentence.append('.');
        return sentence.toString();
    }
}
