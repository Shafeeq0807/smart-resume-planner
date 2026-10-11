package com.smartresume.planner.matching.domain;

import com.smartresume.planner.common.domain.CreatedAtEntity;
import com.smartresume.planner.resume.domain.ResumeVersion;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Match% = 0.45 hard + 0.15 soft + 0.25 semantic + 0.15 title fit (weights configurable). */
@Entity
@Table(name = "job_match_analyses")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobMatchAnalysis extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_version_id", nullable = false)
    private ResumeVersion resumeVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_description_id", nullable = false)
    private JobDescription jobDescription;

    @Column(name = "match_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal matchPercentage;

    @Column(name = "hard_skill_coverage", precision = 5, scale = 2)
    private BigDecimal hardSkillCoverage;

    @Column(name = "soft_skill_coverage", precision = 5, scale = 2)
    private BigDecimal softSkillCoverage;

    @Column(name = "semantic_score", precision = 5, scale = 2)
    private BigDecimal semanticScore;

    @Column(name = "title_fit_score", precision = 5, scale = 2)
    private BigDecimal titleFitScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_recommendations", columnDefinition = "json")
    private List<String> aiRecommendations;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "analyzer_version", nullable = false, length = 20)
    private String analyzerVersion;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "matchAnalysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillGap> skillGaps = new ArrayList<>();

    public JobMatchAnalysis(ResumeVersion resumeVersion, JobDescription jobDescription, String analyzerVersion) {
        this.resumeVersion = resumeVersion;
        this.jobDescription = jobDescription;
        this.analyzerVersion = analyzerVersion;
    }

    public void addSkillGap(SkillGap gap) {
        gap.setMatchAnalysis(this);
        skillGaps.add(gap);
    }

    public List<SkillGap> getSkillGaps() {
        return Collections.unmodifiableList(skillGaps);
    }
}
