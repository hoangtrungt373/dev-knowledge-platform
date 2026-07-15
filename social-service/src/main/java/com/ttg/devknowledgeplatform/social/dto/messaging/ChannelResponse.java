package com.ttg.devknowledgeplatform.social.dto.messaging;

/**
 * A text channel within a group.
 *
 * @param id      primary key
 * @param groupId the owning group's id
 * @param name    channel name, unique within its group
 */
public record ChannelResponse(Integer id, Integer groupId, String name) {
}
