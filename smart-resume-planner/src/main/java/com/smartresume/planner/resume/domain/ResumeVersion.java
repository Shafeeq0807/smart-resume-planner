package com.smartresume.planner.resume.domain;

import com.smartresume.planner.common.domain.CreatedAtEntity;
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

/**
 * Immutable snapshot. Every analysis, rewrite, export and chat session points at a version,
 * so scores stay reproducible after the user edits the resume.
 * List screens should use a DTO projection to avoid loading extracted_text (MEDIUMTEXT).
 */
@Entity
@Table(name = "resume_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeVersion extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    /** Lineage: the version this one was derived from (null for the first upload). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_version_id")
    private ResumeVersion parentVersion;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(length = 100)
    private String label;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private VersionSource source;

    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "checksum_sha256", columnDefinition = "CHAR(64)")
    private String checksumSha256;

    @Column(name = "extracted_text", columnDefinition = "MEDIUMTEXT")
    private String extractedText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "structured_content", columnDefinition = "json")
    private ResumeDocument structuredContent;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "word_count")
    private Integer wordCount;

    /** Version created from an uploaded PDF/DOCX. */
    public static ResumeVersion fromUpload(String originalFilename, String contentType, long sizeBytes,
                                           String storageKey, String checksumSha256, String extractedText,
                                           ResumeDocument structured, Integer pageCount) {
        ResumeVersion v = new ResumeVersion();
        v.source = VersionSource.UPLOAD;
        v.originalFilename = originalFilename;
        v.contentType = contentType;
        v.fileSizeBytes = sizeBytes;
        v.storageKey = storageKey;
        v.checksumSha256 = checksumSha256;
        v.extractedText = extractedText;
        v.structuredContent = structured;
        v.pageCount = pageCount;
        v.wordCount = countWords(extractedText);
        return v;
    }

    /** Version derived from another one (manual edit or accepted AI rewrites). */
    public static ResumeVersion derivedFrom(ResumeVersion parent, VersionSource source, String label,
                                            String newText, ResumeDocument newStructured) {
        ResumeVersion v = new ResumeVersion();
        v.parentVersion = parent;
        v.source = source;
        v.label = label;
        v.extractedText = newText;
        v.structuredContent = newStructured;
        v.wordCount = countWords(newText);
        return v;
    }

    public void setLabelIfAbsent(String label) {
        if (this.label == null) {
            this.label = label;
        }
    }

    public void setParent(ResumeVersion parent) {
        this.parentVersion = parent;
    }

    void attachTo(Resume resume, int versionNumber) {
        this.resume = resume;
        this.versionNumber = versionNumber;
    }

    private static Integer countWords(String text) {
        return text == null || text.isBlank() ? 0 : text.trim().split("\\s+").length;
    }
}
