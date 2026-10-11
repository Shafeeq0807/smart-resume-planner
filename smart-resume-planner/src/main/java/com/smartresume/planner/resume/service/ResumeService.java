package com.smartresume.planner.resume.service;

import com.smartresume.planner.ats.dto.AtsResult;
import com.smartresume.planner.ats.repository.AtsAnalysisRepository;
import com.smartresume.planner.ats.service.AtsService;
import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.common.util.Hashing;
import com.smartresume.planner.common.util.Texts;
import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.resume.domain.Resume;
import com.smartresume.planner.resume.domain.ResumeDocument;
import com.smartresume.planner.resume.domain.ResumeVersion;
import com.smartresume.planner.resume.domain.VersionSource;
import com.smartresume.planner.resume.dto.ResumeDtos.EditVersionRequest;
import com.smartresume.planner.resume.dto.ResumeDtos.ResumeDetail;
import com.smartresume.planner.resume.dto.ResumeDtos.ResumeSummary;
import com.smartresume.planner.resume.dto.ResumeDtos.UpdateResumeRequest;
import com.smartresume.planner.resume.dto.ResumeDtos.VersionDetail;
import com.smartresume.planner.resume.dto.ResumeDtos.VersionSummary;
import com.smartresume.planner.resume.parse.DocumentNormalizer;
import com.smartresume.planner.resume.parse.DocumentTextExtractor;
import com.smartresume.planner.resume.parse.DocumentTextExtractor.Extracted;
import com.smartresume.planner.resume.parse.ParsedResume;
import com.smartresume.planner.resume.parse.ResumeParser;
import com.smartresume.planner.resume.parse.ResumeTextRenderer;
import com.smartresume.planner.resume.repository.ResumeRepository;
import com.smartresume.planner.resume.repository.ResumeVersionRepository;
import com.smartresume.planner.resume.repository.VersionRow;
import com.smartresume.planner.storage.FileStorage;
import com.smartresume.planner.user.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository versionRepository;
    private final AtsAnalysisRepository atsRepository;
    private final AtsService atsService;
    private final UserRepository userRepository;
    private final DocumentTextExtractor extractor;
    private final ResumeParser parser;
    private final FileStorage storage;
    private final TransactionTemplate tx;

    // ------------------------------------------------------------------ create / upload

    /** New resume from an uploaded file: extract text, store the file, create v1, run the ATS rules. */
    public ResumeDetail create(Long userId, MultipartFile file, String title, String targetRole) {
        Upload upload = readUpload(file);
        String storageKey = storeFile(upload);
        try {
            return tx.execute(status -> {
                ParsedResume parsed = parser.parse(upload.extracted().text());
                String finalTitle = Texts.blankToNull(title) != null ? title.trim()
                        : defaultTitle(upload.filename(), parsed);
                Resume resume = new Resume(userRepository.getReferenceById(userId),
                        Texts.truncate(finalTitle, 150), Texts.truncate(Texts.blankToNull(targetRole), 150));
                resumeRepository.save(resume);
                ResumeVersion v = newUploadVersion(upload, storageKey, parsed);
                v.setLabelIfAbsent("Original upload");
                resume.addVersion(v);
                versionRepository.saveAndFlush(v);
                atsService.analyzeAndSave(v);
                log.info("Created resume id={} v{} for user id={}", resume.getId(), v.getVersionNumber(), userId);
                return detail(userId, resume.getId());
            });
        } catch (RuntimeException e) {
            storage.delete(storageKey);
            throw e;
        }
    }

    /** Uploads a new file as the next version of an existing resume. */
    public VersionDetail addUploadedVersion(Long userId, Long resumeId, MultipartFile file, String label) {
        Upload upload = readUpload(file);
        String storageKey = storeFile(upload);
        try {
            return tx.execute(status -> {
                Resume resume = requireResume(userId, resumeId);
                if (versionRepository.existsByResumeIdAndChecksumSha256(resumeId, upload.checksum())) {
                    throw ApiException.conflict("DUPLICATE_VERSION", "This exact file was already uploaded for this resume");
                }
                ParsedResume parsed = parser.parse(upload.extracted().text());
                ResumeVersion v = newUploadVersion(upload, storageKey, parsed);
                v.setParent(resume.getCurrentVersion());
                v.setLabelIfAbsent(Texts.blankToNull(label) != null ? label.trim() : "Uploaded " + upload.filename());
                resume.addVersion(v);
                versionRepository.saveAndFlush(v);
                AtsResult ats = AtsResult.from(atsService.analyzeAndSave(v));
                return VersionDetail.of(v, resumeId, true, ats);
            });
        } catch (RuntimeException e) {
            storage.delete(storageKey);
            throw e;
        }
    }

    /** Manual edit: saves the edited text or structured document as a new immutable version. */
    public VersionDetail addEditedVersion(Long userId, Long resumeId, EditVersionRequest request) {
        return tx.execute(status -> {
            Resume resume = requireResume(userId, resumeId);
            ResumeVersion parent = resume.getCurrentVersion();
            ResumeDocument doc;
            String text;
            if (request.document() != null) {
                doc = DocumentNormalizer.normalize(request.document());
                text = ResumeTextRenderer.toPlainText(doc);
            } else {
                text = request.text().strip();
                doc = parser.parse(text).document();
            }
            if (Texts.wordCount(text) < 20) {
                throw ApiException.badRequest("CONTENT_TOO_SHORT", "The resume content is too short to save");
            }
            ResumeVersion v = ResumeVersion.derivedFrom(parent, VersionSource.MANUAL_EDIT,
                    Texts.blankToNull(request.label()) != null ? request.label().trim() : "Manual edit", text, doc);
            resume.addVersion(v);
            versionRepository.saveAndFlush(v);
            AtsResult ats = AtsResult.from(atsService.analyzeAndSave(v));
            return VersionDetail.of(v, resumeId, true, ats);
        });
    }

    // ------------------------------------------------------------------ read

    public PagedResponse<ResumeSummary> list(Long userId, boolean includeArchived, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        return tx.execute(status -> {
            var result = includeArchived
                    ? resumeRepository.findByUserId(userId, pageable)
                    : resumeRepository.findByUserIdAndArchivedFalse(userId, pageable);
            Set<Long> versionIds = result.getContent().stream().map(Resume::getCurrentVersion)
                    .filter(java.util.Objects::nonNull).map(ResumeVersion::getId).collect(Collectors.toSet());
            Map<Long, Integer> scores = latestScores(versionIds);
            return PagedResponse.of(result, r -> ResumeSummary.of(r,
                    r.getCurrentVersion() == null ? null : r.getCurrentVersion().getVersionNumber(),
                    r.getCurrentVersion() == null ? null : scores.get(r.getCurrentVersion().getId())));
        });
    }

    public ResumeDetail detail(Long userId, Long resumeId) {
        return tx.execute(status -> {
            Resume resume = requireResume(userId, resumeId);
            Long currentId = resume.getCurrentVersion() == null ? null : resume.getCurrentVersion().getId();
            List<VersionRow> rows = versionRepository.findRows(resumeId);
            Map<Long, Integer> scores = latestScores(rows.stream().map(VersionRow::id).collect(Collectors.toSet()));
            List<VersionSummary> versions = rows.stream()
                    .map(r -> VersionSummary.of(r, currentId, scores.get(r.id()))).toList();
            VersionDetail current = null;
            Integer currentNumber = null;
            Integer currentScore = null;
            if (resume.getCurrentVersion() != null) {
                current = versionDetail(userId, resumeId, currentId);
                currentNumber = current.versionNumber();
                currentScore = scores.get(currentId);
            }
            return new ResumeDetail(ResumeSummary.of(resume, currentNumber, currentScore), versions, current);
        });
    }

    public List<VersionSummary> versions(Long userId, Long resumeId) {
        return tx.execute(status -> {
            Resume resume = requireResume(userId, resumeId);
            Long currentId = resume.getCurrentVersion() == null ? null : resume.getCurrentVersion().getId();
            List<VersionRow> rows = versionRepository.findRows(resumeId);
            Map<Long, Integer> scores = latestScores(rows.stream().map(VersionRow::id).collect(Collectors.toSet()));
            return rows.stream().map(r -> VersionSummary.of(r, currentId, scores.get(r.id()))).toList();
        });
    }

    public VersionDetail versionDetail(Long userId, Long resumeId, Long versionId) {
        return tx.execute(status -> {
            Resume resume = requireResume(userId, resumeId);
            ResumeVersion v = requireVersion(userId, resumeId, versionId);
            boolean current = resume.getCurrentVersion() != null && resume.getCurrentVersion().getId().equals(versionId);
            AtsResult ats = atsRepository.findFirstByResumeVersionIdOrderByIdDesc(versionId)
                    .map(AtsResult::from).orElse(null);
            return VersionDetail.of(v, resumeId, current, ats);
        });
    }

    // ------------------------------------------------------------------ update / delete

    public ResumeSummary update(Long userId, Long resumeId, UpdateResumeRequest request) {
        return tx.execute(status -> {
            Resume resume = requireResume(userId, resumeId);
            if (request.title() != null && !request.title().isBlank()) {
                resume.setTitle(request.title().trim());
            }
            if (request.targetRole() != null) {
                resume.setTargetRole(Texts.blankToNull(request.targetRole()));
            }
            if (request.archived() != null) {
                resume.setArchived(request.archived());
            }
            resumeRepository.saveAndFlush(resume);
            Integer number = resume.getCurrentVersion() == null ? null : resume.getCurrentVersion().getVersionNumber();
            Integer score = resume.getCurrentVersion() == null ? null
                    : latestScores(Set.of(resume.getCurrentVersion().getId())).get(resume.getCurrentVersion().getId());
            return ResumeSummary.of(resume, number, score);
        });
    }

    /** Makes an older version current again (restore). Nothing is deleted. */
    public VersionDetail activateVersion(Long userId, Long resumeId, Long versionId) {
        tx.executeWithoutResult(status -> {
            Resume resume = requireResume(userId, resumeId);
            ResumeVersion target = requireVersion(userId, resumeId, versionId);
            resume.switchCurrentVersion(target);
            resumeRepository.saveAndFlush(resume);
        });
        return versionDetail(userId, resumeId, versionId);
    }

    public void delete(Long userId, Long resumeId) {
        tx.executeWithoutResult(status -> {
            Resume resume = resumeRepository.findWithVersionsByIdAndUserId(resumeId, userId)
                    .orElseThrow(() -> ApiException.notFound("Resume"));
            List<String> keys = resume.getVersions().stream().map(ResumeVersion::getStorageKey)
                    .filter(java.util.Objects::nonNull).toList();
            resumeRepository.delete(resume);
            resumeRepository.flush();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    keys.forEach(storage::delete);
                }
            });
            log.info("Deleted resume id={} for user id={}", resumeId, userId);
        });
    }

    // ------------------------------------------------------------------ download

    public record Download(InputStream stream, String filename, String contentType, long size) { }

    public Download openOriginal(Long userId, Long resumeId, Long versionId) {
        ResumeVersion v = tx.execute(status -> requireVersion(userId, resumeId, versionId));
        if (v.getStorageKey() == null) {
            throw ApiException.notFound("Original file (this version was created from an edit)");
        }
        try {
            return new Download(storage.open(v.getStorageKey()),
                    v.getOriginalFilename() == null ? "resume" : v.getOriginalFilename(),
                    v.getContentType() == null ? "application/octet-stream" : v.getContentType(),
                    v.getFileSizeBytes() == null ? -1 : v.getFileSizeBytes());
        } catch (IOException e) {
            log.error("Stored file missing for version id={}: {}", versionId, e.getMessage());
            throw ApiException.notFound("Original file");
        }
    }

    // ------------------------------------------------------------------ helpers

    private record Upload(byte[] bytes, String filename, Extracted extracted, String checksum) { }

    private Upload readUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Please choose a PDF or DOCX file to upload");
        }
        try {
            byte[] bytes = file.getBytes();
            Extracted extracted = extractor.extract(bytes);
            String name = sanitizeFilename(file.getOriginalFilename());
            return new Upload(bytes, name, extracted, Hashing.sha256Hex(bytes));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD_READ_FAILED", "Could not read the uploaded file");
        }
    }

    private String storeFile(Upload upload) {
        try {
            String ext = DocumentTextExtractor.PDF.equals(upload.extracted().mimeType()) ? "pdf" : "docx";
            return storage.store(upload.bytes(), ext);
        } catch (IOException e) {
            log.error("Could not store upload", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_FAILED", "Could not save the file");
        }
    }

    private ResumeVersion newUploadVersion(Upload upload, String storageKey, ParsedResume parsed) {
        return ResumeVersion.fromUpload(upload.filename(), upload.extracted().mimeType(), upload.bytes().length,
                storageKey, upload.checksum(), upload.extracted().text(), parsed.document(),
                upload.extracted().pageCount());
    }

    private static String defaultTitle(String filename, ParsedResume parsed) {
        String name = parsed.document().contact().fullName();
        if (name != null && !name.isBlank()) {
            return name + " - Resume";
        }
        String base = filename.replaceAll("\\.[^.]+$", "").replaceAll("[_-]+", " ").trim();
        return base.isEmpty() ? "My resume" : base;
    }

    /** Drops any path components and odd characters from a client-supplied filename. */
    static String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "resume";
        }
        String base = original.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^\\p{L}\\p{N} ._()-]", "_").trim();
        return Texts.truncate(base.isEmpty() ? "resume" : base, 200);
    }

    private Map<Long, Integer> latestScores(Set<Long> versionIds) {
        Map<Long, Integer> scores = new HashMap<>();
        if (versionIds.isEmpty()) {
            return scores;
        }
        for (Object[] row : atsRepository.latestScores(versionIds)) {
            scores.put((Long) row[0], (Integer) row[1]);
        }
        return scores;
    }

    Resume requireResume(Long userId, Long resumeId) {
        return resumeRepository.findByIdAndUserId(resumeId, userId)
                .orElseThrow(() -> ApiException.notFound("Resume"));
    }

    ResumeVersion requireVersion(Long userId, Long resumeId, Long versionId) {
        return versionRepository.findOwned(versionId, resumeId, userId)
                .orElseThrow(() -> ApiException.notFound("Resume version"));
    }
}
