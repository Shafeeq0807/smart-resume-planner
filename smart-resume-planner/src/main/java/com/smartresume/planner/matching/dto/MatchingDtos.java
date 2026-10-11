package com.smartresume.planner.matching.dto;

import com.smartresume.planner.matching.domain.EvidenceSource;
import com.smartresume.planner.matching.domain.JobDescription;
import com.smartresume.planner.matching.domain.JobMatchAnalysis;
import com.smartresume.planner.matching.domain.JobRequirements;
import com.smartresume.planner.matching.domain.SkillGap;
import com.smartresume.planner.matching.domain.SkillImportance;
import com.smartresume.planner.matching.domain.SkillMatchStatus;
import com.smartresume.planner.matching.domain.SkillType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public final class MatchingDtos {

    private MatchingDtos() {
    }

    public record JobDescriptionRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 200) String company,
            @Size(max = 150) String location,
            @Size(max = 1000) String sourceUrl,
            @NotBlank @Size(min = 80, max = 30_000, message = "must be between 80 and 30000 characters")
            String rawText
    ) { }

    public record JobDescriptionResponse(Long id, String title, String company, String location, String sourceUrl,
                                         String rawText, JobRequirements requirements, String seniority,
                                         Instant createdAt, Instant updatedAt) {
        public static JobDescriptionResponse from(JobDescription j) {
            return new JobDescriptionResponse(j.getId(), j.getTitle(), j.getCompany(), j.getLocation(),
                    j.getSourceUrl(), j.getRawText(), j.getExtractedSkills(), j.getSeniority(), j.getCreatedAt(),
                    j.getUpdatedAt());
        }
    }

    public record JobDescriptionSummary(Long id, String title, String company, String location, String seniority,
                                        Instant createdAt) {
        public static JobDescriptionSummary from(JobDescription j) {
            return new JobDescriptionSummary(j.getId(), j.getTitle(), j.getCompany(), j.getLocation(),
                    j.getSeniority(), j.getCreatedAt());
        }
    }

    public record MatchRequest(
            @NotNull Long resumeId,
            Long versionId,
            @NotNull Long jobDescriptionId,
            Boolean includeGithub,
            Boolean includeAi
    ) { }

    public record SkillGapResponse(String skill, String normalizedName, SkillType type, SkillImportance importance,
                                   SkillMatchStatus status, String evidence, EvidenceSource evidenceSource) {
        static SkillGapResponse from(SkillGap g) {
            return new SkillGapResponse(g.getSkillName(), g.getNormalizedName(), g.getSkillType(), g.getImportance(),
                    g.getStatus(), g.getEvidence(), g.getEvidenceSource());
        }
    }

    public record MatchResponse(Long id, Long resumeId, Long resumeVersionId, int resumeVersionNumber,
                                Long jobDescriptionId, String jobTitle, String company, BigDecimal matchPercentage,
                                String verdict, BigDecimal hardSkillCoverage, BigDecimal softSkillCoverage,
                                BigDecimal semanticScore, BigDecimal titleFitScore, Counts counts,
                                List<SkillGapResponse> skills, List<SkillGapResponse> missingRequired,
                                List<String> recommendations, String aiProvider, Instant createdAt) {

        public record Counts(long matched, long partial, long missing) { }

        public static MatchResponse from(JobMatchAnalysis m) {
            List<SkillGapResponse> skills = m.getSkillGaps().stream()
                    .sorted(Comparator.comparing((SkillGap g) -> g.getStatus().ordinal() == 0 ? 2 : g.getStatus().ordinal() == 1 ? 1 : 0)
                            .thenComparing(g -> g.getImportance().ordinal())
                            .thenComparing(SkillGap::getSkillName))
                    .map(SkillGapResponse::from).toList();
            Counts counts = new Counts(
                    skills.stream().filter(s -> s.status() == SkillMatchStatus.MATCHED).count(),
                    skills.stream().filter(s -> s.status() == SkillMatchStatus.PARTIAL).count(),
                    skills.stream().filter(s -> s.status() == SkillMatchStatus.MISSING).count());
            List<SkillGapResponse> missingRequired = skills.stream()
                    .filter(s -> s.status() == SkillMatchStatus.MISSING && s.importance() == SkillImportance.REQUIRED)
                    .toList();
            return new MatchResponse(m.getId(), m.getResumeVersion().getResume().getId(), m.getResumeVersion().getId(),
                    m.getResumeVersion().getVersionNumber(), m.getJobDescription().getId(),
                    m.getJobDescription().getTitle(), m.getJobDescription().getCompany(), m.getMatchPercentage(),
                    verdict(m.getMatchPercentage()), m.getHardSkillCoverage(), m.getSoftSkillCoverage(),
                    m.getSemanticScore(), m.getTitleFitScore(), counts, skills, missingRequired,
                    m.getAiRecommendations() == null ? List.of() : m.getAiRecommendations(), m.getModelName(),
                    m.getCreatedAt());
        }

        static String verdict(BigDecimal pct) {
            double p = pct.doubleValue();
            return p >= 80 ? "STRONG_MATCH" : p >= 60 ? "GOOD_MATCH" : p >= 40 ? "PARTIAL_MATCH" : "WEAK_MATCH";
        }
    }

    public record MatchSummary(Long id, Long resumeId, Long resumeVersionId, Long jobDescriptionId, String jobTitle,
                               String company, BigDecimal matchPercentage, Instant createdAt) {
        public static MatchSummary from(JobMatchAnalysis m) {
            return new MatchSummary(m.getId(), m.getResumeVersion().getResume().getId(), m.getResumeVersion().getId(),
                    m.getJobDescription().getId(), m.getJobDescription().getTitle(),
                    m.getJobDescription().getCompany(), m.getMatchPercentage(), m.getCreatedAt());
        }
    }
}
