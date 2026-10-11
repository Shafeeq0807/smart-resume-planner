package com.smartresume.planner.github.domain;

import com.smartresume.planner.common.domain.BaseEntity;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Named GithubRepository (not Repository) to avoid clashing with Spring Data's @Repository. */
@Entity
@Table(name = "github_repositories")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubRepository extends BaseEntity {

    @Setter(AccessLevel.PACKAGE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false)
    private GithubAnalysis analysis;

    @Column(name = "repo_name", nullable = false, length = 200)
    private String repoName;

    @Column(name = "repo_url", nullable = false, length = 500)
    private String repoUrl;

    @Column(length = 1000)
    private String description;

    @Column(name = "primary_language", length = 50)
    private String primaryLanguage;

    /** Bytes per language, as returned by GET /repos/{owner}/{repo}/languages. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Long> languages;

    @Column(nullable = false)
    private int stars;

    @Column(nullable = false)
    private int forks;

    @Column(name = "last_pushed_at")
    private Instant lastPushedAt;

    @Column(name = "readme_quality_score")
    private Integer readmeQualityScore;

    @Column(name = "complexity_score")
    private Integer complexityScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_tech", columnDefinition = "json")
    private List<String> detectedTech;

    @Column(name = "suggested_bullet", columnDefinition = "TEXT")
    private String suggestedBullet;

    @Column(name = "included_in_resume", nullable = false)
    private boolean includedInResume;

    public GithubRepository(String repoName, String repoUrl) {
        this.repoName = repoName;
        this.repoUrl = repoUrl;
    }
}
