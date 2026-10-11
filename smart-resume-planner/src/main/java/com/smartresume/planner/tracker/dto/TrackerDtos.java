package com.smartresume.planner.tracker.dto;

import com.smartresume.planner.tracker.domain.ApplicationStatus;
import com.smartresume.planner.tracker.domain.ApplicationStatusHistory;
import com.smartresume.planner.tracker.domain.JobApplication;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class TrackerDtos {

    private TrackerDtos() {
    }

    public record CreateApplicationRequest(
            @NotBlank @Size(max = 200) String companyName,
            @NotBlank @Size(max = 200) String positionTitle,
            @Size(max = 1000) String jobUrl,
            @Size(max = 150) String location,
            @Size(max = 100) String salaryRange,
            ApplicationStatus status,
            Long jobDescriptionId,
            Long resumeVersionId,
            Instant appliedAt,
            Instant nextFollowUpAt,
            @Size(max = 10_000) String notes
    ) { }

    public record UpdateApplicationRequest(
            @NotBlank @Size(max = 200) String companyName,
            @NotBlank @Size(max = 200) String positionTitle,
            @Size(max = 1000) String jobUrl,
            @Size(max = 150) String location,
            @Size(max = 100) String salaryRange,
            Long jobDescriptionId,
            Long resumeVersionId,
            Instant appliedAt,
            Instant nextFollowUpAt,
            @Size(max = 10_000) String notes,
            Long version
    ) { }

    /** Drag-and-drop: put the card into {@code status} at index {@code position} (0 = top of the column). */
    public record MoveRequest(
            @NotNull ApplicationStatus status,
            @NotNull @Min(0) Integer position,
            @Size(max = 500) String note,
            Long version
    ) { }

    public record FromJobRequest(Long resumeVersionId, ApplicationStatus status) { }

    public record HistoryEntry(ApplicationStatus from, ApplicationStatus to, String note, Instant changedAt) {
        static HistoryEntry from(ApplicationStatusHistory h) {
            return new HistoryEntry(h.getFromStatus(), h.getToStatus(), h.getNote(), h.getChangedAt());
        }
    }

    public record ApplicationResponse(Long id, String companyName, String positionTitle, String jobUrl,
                                      String location, String salaryRange, ApplicationStatus status,
                                      double boardOrder, Instant appliedAt, Instant nextFollowUpAt, String notes,
                                      Long jobDescriptionId, Long resumeId, Long resumeVersionId, Long version,
                                      Instant createdAt, Instant updatedAt, List<HistoryEntry> history) {

        public static ApplicationResponse card(JobApplication a) {
            return build(a, null);
        }

        public static ApplicationResponse detail(JobApplication a) {
            return build(a, a.getStatusHistory().stream().map(HistoryEntry::from).toList());
        }

        private static ApplicationResponse build(JobApplication a, List<HistoryEntry> history) {
            return new ApplicationResponse(a.getId(), a.getCompanyName(), a.getPositionTitle(), a.getJobUrl(),
                    a.getLocation(), a.getSalaryRange(), a.getStatus(), a.getBoardOrder(), a.getAppliedAt(),
                    a.getNextFollowUpAt(), a.getNotes(),
                    a.getJobDescription() == null ? null : a.getJobDescription().getId(),
                    a.getResumeVersion() == null ? null : a.getResumeVersion().getResume().getId(),
                    a.getResumeVersion() == null ? null : a.getResumeVersion().getId(), a.getVersion(),
                    a.getCreatedAt(), a.getUpdatedAt(), history);
        }
    }

    public record Column(ApplicationStatus status, int count, List<ApplicationResponse> applications) { }

    public record Board(List<Column> columns, int total) { }

    public record Stats(Map<ApplicationStatus, Long> byStatus, long total, long active, double interviewRatePercent,
                        double offerRatePercent) { }
}
