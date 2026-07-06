package com.ttg.devknowledgeplatform.social.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.social.entity.Friendship;

/**
 * All methods here take an already-canonicalized pair ({@code user1.id < user2.id}) — see
 * {@code FriendServiceImpl.canonicalize}. Callers must not pass pairs in arbitrary order.
 */
@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Integer> {

    boolean existsByUser1AndUser2(User user1, User user2);

    Optional<Friendship> findByUser1AndUser2(User user1, User user2);

    void deleteByUser1AndUser2(User user1, User user2);

    @Query("SELECT f FROM Friendship f WHERE f.user1 = :user OR f.user2 = :user")
    Page<Friendship> findAllForUser(@Param("user") User user, Pageable pageable);

    /**
     * IDs of every user friended with {@code user}, regardless of which canonical side they're
     * stored on. Used by the service to compute mutual-friend counts via set intersection
     * (simpler and safer than a UNION-based single query).
     */
    @Query("SELECT CASE WHEN f.user1 = :user THEN f.user2.id ELSE f.user1.id END "
            + "FROM Friendship f WHERE f.user1 = :user OR f.user2 = :user")
    List<Integer> findFriendUserIds(@Param("user") User user);
}
