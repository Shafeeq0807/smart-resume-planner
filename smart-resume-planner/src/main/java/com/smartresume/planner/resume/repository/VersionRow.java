package com.smartresume.planner.resume.repository;

import com.smartresume.planner.resume.domain.VersionSource;
import java.time.Instant;

/** Lightweight projection of a version for list screens (no extracted text). */
public record VersionRow(Long id, int versionNumber, String label, VersionSource source, String originalFilename,
                         Long fileSizeBytes, Integer pageCount, Integer wordCount, Long parentVersionId,
                         Instant createdAt) {
}
