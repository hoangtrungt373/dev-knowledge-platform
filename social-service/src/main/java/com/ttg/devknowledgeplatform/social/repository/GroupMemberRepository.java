package com.ttg.devknowledgeplatform.social.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.social.entity.Group;
import com.ttg.devknowledgeplatform.social.entity.GroupMember;

/**
 * Repository for {@link GroupMember} — the (group, user) membership row carrying a
 * {@code GroupMemberRole}, used for every role/permission check on group and channel actions.
 */
@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, Integer> {

    Optional<GroupMember> findByGroupAndUser(Group group, User user);

    boolean existsByGroupAndUser(Group group, User user);

    void deleteByGroupAndUser(Group group, User user);
}
