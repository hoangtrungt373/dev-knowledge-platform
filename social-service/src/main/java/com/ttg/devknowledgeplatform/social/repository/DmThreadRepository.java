package com.ttg.devknowledgeplatform.social.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.social.entity.DmThread;

/**
 * Repository for {@link DmThread}. All methods take an already-canonicalized pair
 * ({@code user1.id < user2.id}) — same convention as {@code FriendshipRepository}; callers must
 * not pass pairs in arbitrary order.
 */
@Repository
public interface DmThreadRepository extends JpaRepository<DmThread, Integer> {

    Optional<DmThread> findByUser1AndUser2(User user1, User user2);

    /** {@code user}'s DM conversations, most recently active first (US-6). */
    @Query("SELECT t FROM DmThread t WHERE t.user1 = :user OR t.user2 = :user "
            + "ORDER BY t.lastMessageAt DESC NULLS LAST")
    Page<DmThread> findAllForUser(@Param("user") User user, Pageable pageable);
}
