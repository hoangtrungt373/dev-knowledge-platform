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
 * A single message within a {@link DmThread}. {@code content} and the attachment fields are
 * independently nullable, so a message can carry text only, an attachment only, or both — the
 * MVP's open TBD on that point resolves to "both allowed"; {@link #messageType} just tags which
 * one is the primary content for rendering.
 *
 * <p>Ordering within a thread uses {@code dteCreation} (inherited audit column) rather than an
 * explicit turn-index counter like {@code ChatMessage.turnIndex} — that counter exists there to
 * guarantee strict single-writer USER/ASSISTANT alternation, which doesn't apply to multi-party
 * chat where either participant can write concurrently.
 */
@Entity
@Table(
        name = "DM_MESSAGE",
        schema = "product",
        indexes = @Index(name = "IDX_DM_MESSAGE_THREAD_CREATED", columnList = "DM_THREAD_ID, DTE_CREATION")
)
@AttributeOverride(name = "id", column = @Column(name = "DM_MESSAGE_ID"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"dmThread", "sender"})
@ToString(exclude = {"dmThread", "sender"})
public class DmMessage extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "DM_THREAD_ID", nullable = false)
    private DmThread dmThread;

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
