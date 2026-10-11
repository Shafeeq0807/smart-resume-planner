package com.smartresume.planner.github.dto;

import com.smartresume.planner.common.domain.AsyncJob;
import com.smartresume.planner.common.domain.AsyncJobType;
import com.smartresume.planner.common.domain.ProcessingStatus;
import com.smartresume.planner.github.domain.GithubAnalysis;
import com.smartresume.planner.github.domain.GithubRepository;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GithubDtos {

    private GithubDtos() {
    }

    public record AnalyzeRequest(
            @Pattern(regexp = "^$|^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}$",
                    message = "must be a valid GitHub username") String username,
            Boolean refresh
    ) { }

    public record StartResponse(UUID jobId, Long analysisId, ProcessingStatus status, boolean cached) { }

    public record JobResponse(UUID id, AsyncJobType type, ProcessingStatus status, int progress, String resultType,
                              Long resultId, String errorMessage, Instant createdAt, Instant completedAt) {
        public static JobResponse from(AsyncJob j) {
            return new JobResponse(j.getId(), j.getJobType(), j.getStatus(), j.getProgress(), j.getResultType(),
                    j.getResultId(), j.getErrorMessage(), j.getCreatedAt(), j.getCompletedAt());
        }
    }

    public record RepoResponse(Long id, String name, String url, String description, String primaryLanguage,
                               Map<String, Long> languages, int stars, int forks, Instant lastPushedAt,
                               Integer readmeQualityScore, Integer complexityScore, List<String> technologies,
                               String suggestedBullet, boolean includedInResume) {
        public static RepoResponse from(GithubRepository r) {
            return new RepoResponse(r.getId(), r.getRepoName(), r.getRepoUrl(), r.getDescription(),
                    r.getPrimaryLanguage(), r.getLanguages(), r.getStars(), r.getForks(), r.getLastPushedAt(),
                    r.getReadmeQualityScore(), r.getComplexityScore(), r.getDetectedTech(), r.getSuggestedBullet(),
                    r.isIncludedInResume());
        }
    }

    public record AnalysisResponse(Long id, String githubUsername, ProcessingStatus status, Integer overallScore,
                                   Map<String, Object> profileSummary, List<String> detectedSkills,
                                   String errorMessage, Instant analyzedAt, Instant createdAt,
                                   List<RepoResponse> repositories) {
        public static AnalysisResponse summary(GithubAnalysis a) {
            return build(a, null);
        }

        public static AnalysisResponse detail(GithubAnalysis a) {
            return build(a, a.getRepositories().stream().map(RepoResponse::from).toList());
        }

        private static AnalysisResponse build(GithubAnalysis a, List<RepoResponse> repos) {
            return new AnalysisResponse(a.getId(), a.getGithubUsername(), a.getStatus(), a.getOverallScore(),
                    a.getProfileSummary(), a.getDetectedSkills(), a.getErrorMessage(), a.getAnalyzedAt(),
                    a.getCreatedAt(), repos);
        }
    }

    public record UpdateRepoRequest(@NotNull Boolean includedInResume, @Size(max = 2000) String suggestedBullet) { }

    public record ToResumeRequest(@NotNull Long resumeId, @NotEmpty List<Long> repositoryIds,
                                  @Size(max = 100) String label) { }
}
