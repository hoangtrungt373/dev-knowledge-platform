package com.ttg.devknowledgeplatform.identity.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.identity.entity.User;
import com.ttg.devknowledgeplatform.identity.enums.UserProvider;
import com.ttg.devknowledgeplatform.identity.enums.UserStatus;

/**
 * Read/write access to {@link User} — this module's own repository now that {@code gateway}
 * dropped its local copy and this module became the sole consumer (moved here from {@code common},
 * see {@code docs/CHANGELOG.md}). {@link JpaSpecificationExecutor} is unused today (it supported
 * {@code social-service}'s dynamic user search before that module moved to its own
 * {@code SocialProfile}/{@code SocialProfileRepository} — see that module's {@code CLAUDE.md});
 * kept since removing it isn't a decision this move should make silently.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByUserUuid(String userUuid);

    Optional<User> findByProviderAndProviderId(UserProvider provider, String providerId);

    Optional<User> findBySeedId(String seedId);

    Optional<User> findByKeycloakSubjectId(String keycloakSubjectId);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Integer id);

    boolean existsByUserUuid(String userUuid);

    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :userId")
    void updateStatus(@Param("userId") Integer userId, @Param("status") UserStatus status);
}
