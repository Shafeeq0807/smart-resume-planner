package com.smartresume.planner.ai.dto;

import com.smartresume.planner.ai.domain.BulletRewrite;
import com.smartresume.planner.ai.domain.GeneratedDocument;
import com.smartresume.planner.ai.domain.GeneratedDocumentType;
import com.smartresume.planner.ai.domain.RewriteStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class OptimizerDtos {

    private OptimizerDtos() {
    }

    public record AnalyzeBulletsRequest(@NotNull Long resumeId, Long versionId, Long jobDescriptionId,
                                        @Min(1) @Max(25) Integer maxBullets) { }

    public record BulletRewriteResponse(Long id, Long resumeVersionId, String section, String originalText,
                                        String rewrittenText, boolean needsUserMetric, Integer impactScoreBefore,
                                        Integer impactScoreAfter, RewriteStatus status, Long appliedInVersionId,
                                        Instant createdAt) {
        public static BulletRewriteResponse from(BulletRewrite b) {
            return new BulletRewriteResponse(b.getId(), b.getResumeVersion().getId(), b.getSection(),
                    b.getOriginalText(), b.getRewrittenText(), b.isNeedsUserMetric(), b.getImpactScoreBefore(),
                    b.getImpactScoreAfter(), b.getStatus(),
                    b.getAppliedInVersion() == null ? null : b.getAppliedInVersion().getId(), b.getCreatedAt());
        }
    }

    public record AnalyzeBulletsResponse(Long resumeVersionId, int bulletsScanned, int weakBullets,
                                         List<BulletRewriteResponse> rewrites, String note) { }

    public record ApplyChoice(@NotNull Long rewriteId, @Size(max = 600) String finalText) { }

    public record ApplyRewritesRequest(@NotNull Long resumeId, @NotEmpty @Valid List<ApplyChoice> choices,
                                       @Size(max = 100) String label) { }

    public enum Tone { PROFESSIONAL, ENTHUSIASTIC, CONCISE, FORMAL }

    public record CoverLetterRequest(@NotNull Long resumeId, Long versionId, @NotNull Long jobDescriptionId,
                                     Tone tone, Long applicationId, @Size(max = 1000) String notes) { }

    public record InterviewQuestionsRequest(@NotNull Long resumeId, Long versionId, @NotNull Long jobDescriptionId,
                                            @Min(3) @Max(15) Integer count, Long applicationId) { }

    public record UpdateDocumentRequest(@Size(max = 200) String title, @NotBlank @Size(max = 20_000) String content) { }

    public record DocumentResponse(Long id, GeneratedDocumentType type, String title, String content,
                                   Map<String, Object> structured, String tone, Long resumeVersionId,
                                   Long jobDescriptionId, Long applicationId, String modelName, Instant createdAt,
                                   Instant updatedAt) {
        public static DocumentResponse from(GeneratedDocument d) {
            return new DocumentResponse(d.getId(), d.getDocType(), d.getTitle(), d.getContent(), d.getContentJson(),
                    d.getTone(), d.getResumeVersion().getId(),
                    d.getJobDescription() == null ? null : d.getJobDescription().getId(),
                    d.getApplication() == null ? null : d.getApplication().getId(), d.getModelName(),
                    d.getCreatedAt(), d.getUpdatedAt());
        }
    }

    public record DocumentSummary(Long id, GeneratedDocumentType type, String title, Long jobDescriptionId,
                                  Long applicationId, Instant createdAt) {
        public static DocumentSummary from(GeneratedDocument d) {
            return new DocumentSummary(d.getId(), d.getDocType(), d.getTitle(),
                    d.getJobDescription() == null ? null : d.getJobDescription().getId(),
                    d.getApplication() == null ? null : d.getApplication().getId(), d.getCreatedAt());
        }
    }
}
