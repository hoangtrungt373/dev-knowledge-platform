package com.ttg.devknowledgeplatform.event;

import com.ttg.devknowledgeplatform.infra.event.AsyncEventHandler;
import com.ttg.devknowledgeplatform.infra.event.EventHandler;
import com.ttg.devknowledgeplatform.social.event.FriendRequestSentEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Reacts to a new friend request. Currently just logs — this is the seam where an in-app or
 * email notification would be triggered once notification delivery exists for this domain,
 * mirroring how {@link ContentPublishedEventListener} exists purely to kick off indexing.
 */
@Component
@Slf4j
public class FriendRequestSentEventListener extends AsyncEventHandler<FriendRequestSentEvent> {

    @EventHandler
    public void onEvent(FriendRequestSentEvent event) {
        handle(event);
    }

    @Override
    protected void doHandle(FriendRequestSentEvent event) {
        log.info("FriendRequestSentEvent received: requestId={} requesterId={} addresseeId={}",
                event.requestId(), event.requesterId(), event.addresseeId());
    }
}
