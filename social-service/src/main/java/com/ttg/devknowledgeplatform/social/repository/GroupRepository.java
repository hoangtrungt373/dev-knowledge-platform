package com.ttg.devknowledgeplatform.social.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.social.entity.Group;

/**
 * Repository for {@link Group}. Role/ownership queries live on {@link GroupMemberRepository}
 * instead — a group's own row never carries membership data.
 */
@Repository
public interface GroupRepository extends JpaRepository<Group, Integer> {

    /**
     * Groups {@code user} belongs to, newest first. Ordering by group id is a placeholder — no
     * "recent activity" definition is locked for groups yet (unlike DM's {@code lastMessageAt}),
     * so this is the simplest stable order until that's decided.
     */
    @Query("SELECT gm.group FROM GroupMember gm WHERE gm.user = :user ORDER BY gm.group.id DESC")
    Page<Group> findAllForUser(@Param("user") User user, Pageable pageable);
}
