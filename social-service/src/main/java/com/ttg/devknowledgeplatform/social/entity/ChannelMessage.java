package com.ttg.devknowledgeplatform.social.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;
import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.social.enums.MessageType;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A single message within a {@link Channel}. Field shape mirrors {@link DmMessage} exactly (both
 * intentionally flat, single-level entities — see the module design notes on why a shared
 * {@code @MappedSuperclass} was rejected here): {@code content} and the attachment fields are
 * independently nullable, and {@link #messageType} only tags the primary content for rendering.
 */
@Entity
@Table(
        name = "CHANNEL_MESSAGE",
        schema = "product",
        indexes = @Index(name = "IDX_CHANNEL_MESSAGE_CHANNEL_CREATED", columnList = "CHANNEL_ID, DTE_CREATION")
)
@AttributeOverride(name = "id", column = @Column(name = "CHANNEL_MESSAGE_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"channel", "sender"})
@ToString(exclude = {"channel", "sender"})
public class ChannelMessage extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CHANNEL_ID", nullable = false)
    private Channel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SENDER_ID", nullable = false)
    private User sender;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "MESSAGE_TYPE", length = 20, nullable = false)
    private MessageType messageType;

    @Column(name = "CONTENT", columnDefinition = "TEXT")
    private String content;

    /** MinIO object key — resolved to a presigned URL at read time, same pattern as avatar images. */
    @Column(name = "ATTACHMENT_OBJECT_KEY", length = 500)
    private String attachmentObjectKey;

    @Column(name = "ATTACHMENT_MIME_TYPE", length = 100)
    private String attachmentMimeType;

    @Column(name = "ATTACHMENT_FILE_NAME", length = 255)
    private String attachmentFileName;

    @Column(name = "ATTACHMENT_FILE_SIZE")
    private Long attachmentFileSize;
}
