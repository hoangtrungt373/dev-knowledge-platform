package com.ttg.devknowledgeplatform.social.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.social.entity.SocialProfile;

/**
 * This module's own repository over {@link SocialProfile} — the replacement for {@code common}'s
 * shared {@code UserRepository}, which this module no longer uses at all now that it maps its own
 * lean entity instead of {@code common.entity.User}.
 */
@Repository
public interface SocialProfileRepository extends JpaRepository<SocialProfile, Integer>,
        JpaSpecificationExecutor<SocialProfile> {

    Optional<SocialProfile> findByProfileUuid(String profileUuid);

    Optional<SocialProfile> findByEmail(String email);

    Optional<SocialProfile> findByKeycloakSubjectId(String keycloakSubjectId);

    Optional<SocialProfile> findBySeedId(String seedId);
}
