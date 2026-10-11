package com.smartresume.planner.matching.domain;

import com.smartresume.planner.common.domain.BaseEntity;
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
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One JD skill and whether the resume (or GitHub) proves it. Normalised for analytics. */
@Entity
@Table(name = "skill_gaps")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkillGap extends BaseEntity {

    @Setter(AccessLevel.PACKAGE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_analysis_id", nullable = false)
    private JobMatchAnalysis matchAnalysis;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Column(name = "normalized_name", nullable = false, length = 100)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "skill_type", nullable = false, length = 20)
    private SkillType skillType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SkillImportance importance;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SkillMatchStatus status;

    @Column(length = 500)
    private String evidence;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "evidence_source", length = 20)
    private EvidenceSource evidenceSource;

    public SkillGap(String skillName, String normalizedName, SkillType skillType, SkillImportance importance,
                    SkillMatchStatus status, String evidence, EvidenceSource evidenceSource) {
        this.skillName = skillName;
        this.normalizedName = normalizedName;
        this.skillType = skillType;
        this.importance = importance;
        this.status = status;
        this.evidence = evidence;
        this.evidenceSource = evidenceSource;
    }
}
