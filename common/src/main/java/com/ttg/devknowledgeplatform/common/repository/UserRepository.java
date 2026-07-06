package com.ttg.devknowledgeplatform.common.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.common.enums.UserProvider;
import com.ttg.devknowledgeplatform.common.enums.UserStatus;

/**
 * Read/write access to {@link User}, shared across every module. Lives in {@code common} (not
 * {@code api}) so feature modules that can't depend on {@code api} — {@code content-service},
 * {@code social-service} — can reach it directly, the same reasoning behind {@code SysParamRepository}
 * living here. Extends {@link JpaSpecificationExecutor} for {@code social-service}'s dynamic user
 * search (see its {@code UserSpecification}).
 */
@Repository
public interface UserRepository extends JpaRepository<User, Integer>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByUserUuid(String userUuid);

    Optional<User> findByProviderAndProviderId(UserProvider provider, String providerId);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Integer id);

    boolean existsByUserUuid(String userUuid);

    @Modifying
    @Query("UPDATE User u SET u.status = :status WHERE u.id = :userId")
    void updateStatus(@Param("userId") Integer userId, @Param("status") UserStatus status);
}
