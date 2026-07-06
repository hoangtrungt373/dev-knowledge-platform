package com.ttg.devknowledgeplatform.social.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.common.entity.User;

/**
 * Read access to {@link User} for the social module (search, relationship resolution).
 *
 * <p>Named distinctly from {@code api}'s own {@code UserRepository} over the same entity —
 * {@code social-service} cannot depend on {@code api} (that dependency runs the other way), so
 * it needs its own repository interface. Do not rename this to {@code UserRepository}: two
 * Spring Data repositories with the same simple name in different packages both default to the
 * bean name {@code userRepository} and collide at startup.
 */
@Repository
public interface SocialUserRepository extends JpaRepository<User, Integer>, JpaSpecificationExecutor<User> {

    Optional<User> findByUserUuid(String userUuid);
}
