package com.smartresume.planner.github.domain;

import com.smartresume.planner.common.domain.CreatedAtEntity;
import com.smartresume.planner.common.domain.ProcessingStatus;
import com.smartresume.planner.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "github_analyses")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubAnalysis extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "github_username", nullable = false, length = 39)
    private String githubUsername;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ProcessingStatus status = ProcessingStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "profile_summary", columnDefinition = "json")
    private Map<String, Object> profileSummary;

    @Column(name = "overall_score")
    private Integer overallScore;

    /** Normalised technologies proven on GitHub; extra evidence for the job matcher. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detected_skills", columnDefinition = "json")
    private List<String> detectedSkills;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GithubRepository> repositories = new ArrayList<>();

    public GithubAnalysis(User user, String githubUsername) {
        this.user = user;
        this.githubUsername = githubUsername;
    }

    public void addRepository(GithubRepository repository) {
        repository.setAnalysis(this);
        repositories.add(repository);
    }

    public List<GithubRepository> getRepositories() {
        return Collections.unmodifiableList(repositories);
    }
}
