package com.smartresume.planner.export.domain;

import com.smartresume.planner.common.domain.CreatedAtEntity;
import com.smartresume.planner.common.domain.ProcessingStatus;
import com.smartresume.planner.resume.domain.ResumeVersion;
import com.smartresume.planner.user.domain.User;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "pdf_exports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PdfExport extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_version_id", nullable = false)
    private ResumeVersion resumeVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private ResumeTemplate template;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ProcessingStatus status = ProcessingStatus.PENDING;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "page_count")
    private Integer pageCount;

    /** Font, accent colour, margins, section order chosen in the export dialog. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> settings;

    /** Parseability score of the exported PDF, re-run through Tika and the ATS rules. */
    @Column(name = "ats_self_check_score")
    private Integer atsSelfCheckScore;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    public PdfExport(User user, ResumeVersion resumeVersion, ResumeTemplate template, Map<String, Object> settings) {
        this.user = user;
        this.resumeVersion = resumeVersion;
        this.template = template;
        this.settings = settings;
    }

    public void markCompleted(String storageKey, long sizeBytes, int pageCount, int atsSelfCheckScore) {
        this.status = ProcessingStatus.COMPLETED;
        this.storageKey = storageKey;
        this.fileSizeBytes = sizeBytes;
        this.pageCount = pageCount;
        this.atsSelfCheckScore = atsSelfCheckScore;
    }

    public void markFailed(String message) {
        this.status = ProcessingStatus.FAILED;
        this.errorMessage = message == null ? null : message.substring(0, Math.min(message.length(), 1000));
    }
}
