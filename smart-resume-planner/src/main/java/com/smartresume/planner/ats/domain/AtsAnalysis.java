package com.smartresume.planner.ats.domain;

import com.smartresume.planner.common.domain.CreatedAtEntity;
import com.smartresume.planner.resume.domain.ResumeVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/** Deterministic ATS score for one version. Category maxima: 20 / 25 / 20 / 15 / 20. */
@Entity
@Table(name = "ats_analyses")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AtsAnalysis extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_version_id", nullable = false)
    private ResumeVersion resumeVersion;

    @Column(name = "overall_score", nullable = false)
    private int overallScore;

    @Column(name = "parseability_score", nullable = false)
    private int parseabilityScore;

    @Column(name = "structure_score", nullable = false)
    private int structureScore;

    @Column(name = "formatting_score", nullable = false)
    private int formattingScore;

    @Column(name = "title_contact_score", nullable = false)
    private int titleContactScore;

    @Column(name = "content_score", nullable = false)
    private int contentScore;

    @Column(name = "detected_title", length = 150)
    private String detectedTitle;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_sections", columnDefinition = "json")
    private List<String> detectedSections;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "issues", columnDefinition = "json")
    private List<AtsIssue> issues;

    /** Optional LLM-written plain-language summary of the issues. */
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "analyzer_version", nullable = false, length = 20)
    private String analyzerVersion;

    public AtsAnalysis(ResumeVersion resumeVersion, String analyzerVersion) {
        this.resumeVersion = resumeVersion;
        this.analyzerVersion = analyzerVersion;
    }

    public void recomputeOverall() {
        this.overallScore = parseabilityScore + structureScore + formattingScore + titleContactScore + contentScore;
    }
}
