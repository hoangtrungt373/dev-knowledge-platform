package com.ttg.devknowledgeplatform.dto.messaging;

/**
 * A group chat.
 *
 * @param id   primary key
 * @param name display name
 */
public record GroupResponse(Integer id, String name) {
}
