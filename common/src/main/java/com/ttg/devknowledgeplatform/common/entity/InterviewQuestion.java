package com.ttg.devknowledgeplatform.common.entity;

import com.ttg.devknowledgeplatform.common.enums.Difficulty;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "INTERVIEW_QUESTION", schema = "product")
@AttributeOverride(name = "id", column = @Column(name = "INTERVIEW_QUESTION_ID"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = {"contentItem"})
@ToString(exclude = {"contentItem"})
public class InterviewQuestion extends AbstractEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CONTENT_ITEM_ID", nullable = false, unique = true)
    private ContentItem contentItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "DIFFICULTY", length = 50, nullable = false)
    private Difficulty difficulty = Difficulty.INTERMEDIATE;

    @Column(name = "QUESTION_BODY", nullable = false, columnDefinition = "TEXT")
    private String questionBody;

    @Column(name = "SHORT_ANSWER", columnDefinition = "TEXT")
    private String shortAnswer;

    @Column(name = "DETAILED_ANSWER", columnDefinition = "TEXT")
    private String detailedAnswer;

    @Column(name = "IS_COMMON", nullable = false)
    private Boolean isCommon = false;
}
