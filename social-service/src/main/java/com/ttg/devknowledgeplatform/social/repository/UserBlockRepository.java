package com.ttg.devknowledgeplatform.social.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.social.entity.UserBlock;

@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Integer> {

    boolean existsByBlockerAndBlocked(User blocker, User blocked);

    void deleteByBlockerAndBlocked(User blocker, User blocked);

    Page<UserBlock> findByBlocker(User blocker, Pageable pageable);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM UserBlock b "
            + "WHERE (b.blocker = :a AND b.blocked = :b) OR (b.blocker = :b AND b.blocked = :a)")
    boolean existsEitherDirection(@Param("a") User a, @Param("b") User b);
}
