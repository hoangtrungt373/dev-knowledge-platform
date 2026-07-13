package com.ttg.devknowledgeplatform.social.enums;

/**
 * Primary content type of a {@code DmMessage}/{@code ChannelMessage}, used for rendering and
 * future per-type validation. Text and an attachment may still coexist on the same row — this
 * only tags which one is the primary content for display purposes.
 */
public enum MessageType {
    TEXT,
    IMAGE,
    FILE
}
