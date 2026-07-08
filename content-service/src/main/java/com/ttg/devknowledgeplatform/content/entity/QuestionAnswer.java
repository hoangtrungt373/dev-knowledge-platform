package com.ttg.devknowledgeplatform.content.entity;

import com.ttg.devknowledgeplatform.common.entity.AbstractEntity;

import com.ttg.devknowledgeplatform.content.enums.QuestionDifficulty;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * A question-and-answer content item — general dev-knowledge Q&A, not just interview prep.
 *
 * <p>{@code difficulty} and {@code isCommon} are nullable: they're interview-specific metadata,
 * populated when a question genuinely has that framing, left {@code null} for plain "how does
 * X work" knowledge content where forcing an interview-difficulty/frequency judgment call
 * wouldn't mean anything.
 */
@Entity
@Table(name = "QUESTION_ANSWER", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "QUESTION_ANSWER_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"contentItem"})
@ToString(exclude = {"contentItem"})
public class QuestionAnswer extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONTENT_ITEM_ID", nullable = false, unique = true)
    private ContentItem contentItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "DIFFICULTY", length = 50)
    private QuestionDifficulty difficulty;

    @Column(name = "QUESTION_BODY", nullable = false, columnDefinition = "TEXT")
    private String questionBody;

    @Column(name = "SHORT_ANSWER", columnDefinition = "TEXT")
    private String shortAnswer;

    @Column(name = "DETAILED_ANSWER", columnDefinition = "TEXT")
    private String detailedAnswer;

    @Column(name = "IS_COMMON")
    private Boolean isCommon;
}
