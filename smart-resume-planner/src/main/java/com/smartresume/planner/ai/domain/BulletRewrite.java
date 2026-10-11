package com.smartresume.planner.ai.domain;

import com.smartresume.planner.common.domain.AuditableEntity;
import com.smartresume.planner.resume.domain.ResumeVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** XYZ-format suggestion ("Accomplished X using Y resulting in Z") for one bullet. */
@Entity
@Table(name = "bullet_rewrites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BulletRewrite extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_version_id", nullable = false)
    private ResumeVersion resumeVersion;

    /** Set when the rewrite is accepted and a new AI_REWRITE version is created. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_in_version_id")
    private ResumeVersion appliedInVersion;

    @Column(length = 50)
    private String section;

    @Column(name = "original_text", nullable = false, columnDefinition = "TEXT")
    private String originalText;

    @Column(name = "rewritten_text", nullable = false, columnDefinition = "TEXT")
    private String rewrittenText;

    /** True when the model inserted a placeholder such as [X%] instead of inventing a number. */
    @Column(name = "needs_user_metric", nullable = false)
    private boolean needsUserMetric;

    @Column(name = "impact_score_before")
    private Integer impactScoreBefore;

    @Column(name = "impact_score_after")
    private Integer impactScoreAfter;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private RewriteStatus status = RewriteStatus.SUGGESTED;

    @Column(name = "model_name", length = 100)
    private String modelName;

    public BulletRewrite(ResumeVersion resumeVersion, String section, String originalText, String rewrittenText,
                         boolean needsUserMetric, Integer impactScoreBefore, Integer impactScoreAfter,
                         String modelName) {
        this.resumeVersion = resumeVersion;
        this.section = section == null || section.length() <= 50 ? section : section.substring(0, 50);
        this.originalText = originalText;
        this.rewrittenText = rewrittenText;
        this.needsUserMetric = needsUserMetric;
        this.impactScoreBefore = impactScoreBefore;
        this.impactScoreAfter = impactScoreAfter;
        this.modelName = modelName;
    }

    /** The user may edit the text (e.g. fill in the [X%] placeholder) before accepting. */
    public void accept(String finalText, ResumeVersion appliedIn) {
        this.rewrittenText = finalText;
        this.needsUserMetric = false;
        this.appliedInVersion = appliedIn;
        this.status = RewriteStatus.ACCEPTED;
    }

    public void reject() {
        this.status = RewriteStatus.REJECTED;
    }
}
