package com.ttg.devknowledgeplatform.social.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.social.entity.Channel;
import com.ttg.devknowledgeplatform.social.entity.ChannelMessage;

/** Repository for {@link ChannelMessage} — paginated history lookup for a single {@link Channel} (US-18). */
@Repository
public interface ChannelMessageRepository extends JpaRepository<ChannelMessage, Integer> {

    Page<ChannelMessage> findByChannelOrderByDteCreationDesc(Channel channel, Pageable pageable);
}
