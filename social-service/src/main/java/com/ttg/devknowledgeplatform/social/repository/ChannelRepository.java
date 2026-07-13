package com.ttg.devknowledgeplatform.social.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.social.entity.Channel;
import com.ttg.devknowledgeplatform.social.entity.Group;

/**
 * Repository for {@link Channel}. Every group member can see every channel in this MVP, so there
 * is no user-scoped visibility filter here — membership itself is checked via
 * {@link GroupMemberRepository} before a caller ever reaches these methods.
 */
@Repository
public interface ChannelRepository extends JpaRepository<Channel, Integer> {

    List<Channel> findByGroup(Group group);

    /** Pre-check before create, so a duplicate name fails with a clean business error rather than a raw constraint violation. */
    boolean existsByGroupAndName(Group group, String name);
}
