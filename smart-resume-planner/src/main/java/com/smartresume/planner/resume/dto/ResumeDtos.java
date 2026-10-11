package com.smartresume.planner.resume.dto;

import com.smartresume.planner.ats.dto.AtsResult;
import com.smartresume.planner.resume.domain.Resume;
import com.smartresume.planner.resume.domain.ResumeDocument;
import com.smartresume.planner.resume.domain.ResumeVersion;
import com.smartresume.planner.resume.domain.VersionSource;
import com.smartresume.planner.resume.repository.VersionRow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ResumeDtos {

    private ResumeDtos() {
    }

    public record ResumeSummary(Long id, String title, String targetRole, boolean archived, Long currentVersionId,
                                Integer currentVersionNumber, Integer atsScore, Instant createdAt,
                                Instant updatedAt) {
        public static ResumeSummary of(Resume r, Integer versionNumber, Integer atsScore) {
            return new ResumeSummary(r.getId(), r.getTitle(), r.getTargetRole(), r.isArchived(),
                    r.getCurrentVersion() == null ? null : r.getCurrentVersion().getId(), versionNumber, atsScore,
                    r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    public record VersionSummary(Long id, int versionNumber, String label, VersionSource source,
                                 String originalFilename, Long fileSizeBytes, Integer pageCount, Integer wordCount,
                                 Long parentVersionId, boolean current, Integer atsScore, Instant createdAt) {
        public static VersionSummary of(VersionRow r, Long currentVersionId, Integer atsScore) {
            return new VersionSummary(r.id(), r.versionNumber(), r.label(), r.source(), r.originalFilename(),
                    r.fileSizeBytes(), r.pageCount(), r.wordCount(), r.parentVersionId(),
                    r.id().equals(currentVersionId), atsScore, r.createdAt());
        }
    }

    public record VersionDetail(Long id, Long resumeId, int versionNumber, String label, VersionSource source,
                                String originalFilename, String contentType, Long fileSizeBytes, Integer pageCount,
                                Integer wordCount, Long parentVersionId, boolean current, boolean hasOriginalFile,
                                String extractedText, ResumeDocument structuredContent, AtsResult ats,
                                Instant createdAt) {
        public static VersionDetail of(ResumeVersion v, Long resumeId, boolean current, AtsResult ats) {
            return new VersionDetail(v.getId(), resumeId, v.getVersionNumber(), v.getLabel(), v.getSource(),
                    v.getOriginalFilename(), v.getContentType(), v.getFileSizeBytes(), v.getPageCount(),
                    v.getWordCount(), v.getParentVersion() == null ? null : v.getParentVersion().getId(), current,
                    v.getStorageKey() != null, v.getExtractedText(), v.getStructuredContent(), ats,
                    v.getCreatedAt());
        }
    }

    public record ResumeDetail(ResumeSummary resume, List<VersionSummary> versions, VersionDetail currentVersion) { }

    public record UpdateResumeRequest(@Size(min = 1, max = 150) String title,
                                      @Size(max = 150) String targetRole,
                                      Boolean archived) { }

    /** Provide exactly one of {@code text} (re-parsed into sections) or {@code document} (already structured). */
    public record EditVersionRequest(@Size(max = 100) String label,
                                     @Size(max = 200_000) String text,
                                     @Valid ResumeDocument document) {
        @AssertTrue(message = "provide exactly one of 'text' or 'document'")
        public boolean isExactlyOneContent() {
            return (text != null && !text.isBlank()) ^ (document != null);
        }
    }

    public record CompareResponse(Long fromVersionId, Long toVersionId, int fromVersionNumber, int toVersionNumber,
                                  Integer fromAtsScore, Integer toAtsScore, Integer wordCountDelta,
                                  int addedLines, int removedLines, int unchangedLines, List<DiffRow> rows) { }

    public record DiffRow(Type type, String left, String right) {
        public enum Type { EQUAL, INSERT, DELETE, CHANGE }
    }
}
