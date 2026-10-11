package com.smartresume.planner.resume.service;

import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.resume.domain.Resume;
import com.smartresume.planner.resume.domain.ResumeDocument;
import com.smartresume.planner.resume.domain.ResumeVersion;
import com.smartresume.planner.resume.parse.DocumentNormalizer;
import com.smartresume.planner.resume.parse.ResumeParser;
import com.smartresume.planner.resume.repository.ResumeRepository;
import com.smartresume.planner.resume.repository.ResumeVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single entry point for "give me the user's resume version" used by every feature module.
 * Every lookup is scoped by the authenticated user, so a foreign id behaves exactly like a missing one (404).
 */
@Service
@RequiredArgsConstructor
public class ResumeLookup {

    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository versionRepository;
    private final ResumeParser parser;

    /** Plain data, safe to use outside a transaction and to pass to AI prompts. */
    public record Snapshot(Long resumeId, Long versionId, int versionNumber, String resumeTitle, String targetRole,
                           String text, ResumeDocument document) { }

    @Transactional(readOnly = true)
    public Snapshot snapshot(Long userId, Long resumeId, Long versionIdOrNull) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> ApiException.notFound("Resume"));
        ResumeVersion version;
        if (versionIdOrNull == null) {
            version = resume.getCurrentVersion();
            if (version == null) {
                throw ApiException.notFound("Resume version");
            }
        } else {
            version = versionRepository.findOwned(versionIdOrNull, resumeId, userId)
                    .orElseThrow(() -> ApiException.notFound("Resume version"));
        }
        return toSnapshot(resume, version);
    }

    /** For features that only carry a versionId (chat sessions, exports). */
    @Transactional(readOnly = true)
    public Snapshot snapshotByVersion(Long userId, Long versionId) {
        ResumeVersion version = versionRepository.findOwnedByUser(versionId, userId)
                .orElseThrow(() -> ApiException.notFound("Resume version"));
        return toSnapshot(version.getResume(), version);
    }

    private Snapshot toSnapshot(Resume resume, ResumeVersion version) {
        String text = version.getExtractedText() == null ? "" : version.getExtractedText();
        ResumeDocument doc = version.getStructuredContent() != null
                ? DocumentNormalizer.normalize(version.getStructuredContent())
                : parser.parse(text).document();
        return new Snapshot(resume.getId(), version.getId(), version.getVersionNumber(), resume.getTitle(),
                resume.getTargetRole(), text, doc);
    }
}
