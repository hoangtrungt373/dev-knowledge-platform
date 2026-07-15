package com.ttg.devknowledgeplatform.social.event;

import com.ttg.devknowledgeplatform.infra.event.AsyncEventHandler;
import com.ttg.devknowledgeplatform.infra.event.EventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reacts to a friendship being established (explicit accept or mutual auto-accept). Currently
 * just logs — this is the seam where an in-app or email notification would be triggered once
 * notification delivery exists for this domain.
 */
@Component
@Slf4j
public class FriendRequestAcceptedEventListener extends AsyncEventHandler<FriendRequestAcceptedEvent> {

    @EventHandler
    public void onEvent(FriendRequestAcceptedEvent event) {
        handle(event);
    }

    @Override
    protected void doHandle(FriendRequestAcceptedEvent event) {
        log.info("FriendRequestAcceptedEvent received: friendshipId={} user1Id={} user2Id={}",
                event.friendshipId(), event.user1Id(), event.user2Id());
    }
}
