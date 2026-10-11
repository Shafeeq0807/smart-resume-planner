package com.smartresume.planner.export.dto;

import com.smartresume.planner.common.domain.ProcessingStatus;
import com.smartresume.planner.export.domain.PdfExport;
import com.smartresume.planner.export.domain.ResumeTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ExportDtos {

    private ExportDtos() {
    }

    public record TemplateResponse(Long id, String code, String name, String description, boolean atsSafe) {
        public static TemplateResponse from(ResumeTemplate t) {
            return new TemplateResponse(t.getId(), t.getCode(), t.getName(), t.getDescription(), t.isAtsSafe());
        }
    }

    /** All fields optional; defaults are applied server-side. */
    public record ExportSettings(
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex colour like #1f4e79") String accentColor,
            @DecimalMin("9.0") @DecimalMax("12.5") Double fontSizePt,
            @DecimalMin("10.0") @DecimalMax("25.0") Double marginMm
    ) {
        public static final String DEFAULT_ACCENT = "#1f4e79";

        public String accent() {
            return accentColor == null ? DEFAULT_ACCENT : accentColor;
        }

        public double font() {
            return fontSizePt == null ? 10.5 : fontSizePt;
        }

        public double margin() {
            return marginMm == null ? 15.0 : marginMm;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("accentColor", accent());
            m.put("fontSizePt", font());
            m.put("marginMm", margin());
            return m;
        }
    }

    public record ExportRequest(@NotNull Long resumeId, Long versionId, @NotBlank String templateCode,
                                @Valid ExportSettings settings) { }

    public record ExportResponse(Long id, ProcessingStatus status, String templateCode, Long resumeVersionId,
                                 Integer pageCount, Long fileSizeBytes, Integer atsSelfCheckScore,
                                 Map<String, Object> settings, String errorMessage, String downloadUrl,
                                 Instant createdAt) {
        public static ExportResponse from(PdfExport e) {
            return new ExportResponse(e.getId(), e.getStatus(), e.getTemplate().getCode(),
                    e.getResumeVersion().getId(), e.getPageCount(), e.getFileSizeBytes(), e.getAtsSelfCheckScore(),
                    e.getSettings(), e.getErrorMessage(),
                    e.getStatus() == ProcessingStatus.COMPLETED ? "/api/exports/" + e.getId() + "/download" : null,
                    e.getCreatedAt());
        }
    }
}
