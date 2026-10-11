package com.smartresume.planner.export.service;

import com.smartresume.planner.common.domain.ProcessingStatus;
import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.export.domain.PdfExport;
import com.smartresume.planner.export.domain.ResumeTemplate;
import com.smartresume.planner.export.dto.ExportDtos.ExportRequest;
import com.smartresume.planner.export.dto.ExportDtos.ExportResponse;
import com.smartresume.planner.export.dto.ExportDtos.ExportSettings;
import com.smartresume.planner.export.dto.ExportDtos.TemplateResponse;
import com.smartresume.planner.export.repository.PdfExportRepository;
import com.smartresume.planner.export.repository.ResumeTemplateRepository;
import com.smartresume.planner.resume.parse.ResumeTextRenderer;
import com.smartresume.planner.resume.repository.ResumeVersionRepository;
import com.smartresume.planner.resume.service.ResumeLookup;
import com.smartresume.planner.resume.service.ResumeLookup.Snapshot;
import com.smartresume.planner.storage.FileStorage;
import com.smartresume.planner.user.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/** Feature 3: renders a resume version into an ATS-safe PDF (single column, real text, no images). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final ResumeLookup resumeLookup;
    private final ResumeTemplateRepository templateRepository;
    private final PdfExportRepository exportRepository;
    private final ResumeVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final SpringTemplateEngine templateEngine;
    private final PdfRenderer pdfRenderer;
    private final FileStorage storage;
    private final TransactionTemplate tx;

    public List<TemplateResponse> templates() {
        return tx.execute(s -> templateRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(TemplateResponse::from).toList());
    }

    public ExportResponse export(Long userId, ExportRequest request) {
        Snapshot resume = resumeLookup.snapshot(userId, request.resumeId(), request.versionId());
        ExportSettings settings = request.settings() == null ? new ExportSettings(null, null, null) : request.settings();
        ResumeTemplate template = tx.execute(s -> templateRepository.findByCodeAndActiveTrue(request.templateCode())
                .orElseThrow(() -> ApiException.notFound("Template \"" + request.templateCode() + "\"")));

        Long exportId = tx.execute(s -> exportRepository.saveAndFlush(new PdfExport(
                userRepository.getReferenceById(userId), versionRepository.getReferenceById(resume.versionId()),
                templateRepository.getReferenceById(template.getId()), settings.toMap())).getId());

        String storageKey = null;
        try {
            Context ctx = new Context(Locale.ENGLISH);
            ctx.setVariable("doc", resume.document());
            ctx.setVariable("s", settings);
            String html = templateEngine.process(template.getTemplatePath(), ctx);
            PdfRenderer.Rendered pdf = pdfRenderer.render(html);
            int selfCheck = PdfRenderer.selfCheckScore(ResumeTextRenderer.toPlainText(resume.document()),
                    pdf.extractedText());
            storageKey = storage.store(pdf.bytes(), "pdf");
            String key = storageKey;
            return tx.execute(s -> {
                PdfExport e = exportRepository.findById(exportId).orElseThrow();
                e.markCompleted(key, pdf.bytes().length, pdf.pageCount(), selfCheck);
                exportRepository.saveAndFlush(e);
                log.info("Exported resume v{} as PDF id={} ({} pages, self-check {}%)", resume.versionNumber(),
                        exportId, pdf.pageCount(), selfCheck);
                return ExportResponse.from(exportRepository.findByIdAndUserId(exportId, userId).orElseThrow());
            });
        } catch (IOException | RuntimeException e) {
            log.error("PDF export {} failed", exportId, e);
            if (storageKey != null) {
                storage.delete(storageKey);
            }
            tx.executeWithoutResult(s -> exportRepository.findById(exportId)
                    .ifPresent(x -> x.markFailed("PDF generation failed: " + e.getClass().getSimpleName())));
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "EXPORT_FAILED",
                    "Could not generate the PDF. Please try again or choose another template.");
        }
    }

    public PagedResponse<ExportResponse> list(Long userId, int page, int size) {
        return tx.execute(s -> PagedResponse.of(exportRepository.findByUserId(userId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt"))), ExportResponse::from));
    }

    public ExportResponse get(Long userId, Long id) {
        return tx.execute(s -> ExportResponse.from(exportRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Export"))));
    }

    public record PdfFile(InputStream stream, String filename, long size) { }

    public PdfFile open(Long userId, Long id) {
        record Meta(String key, String filename, Long size) { }
        Meta meta = tx.execute(s -> {
            PdfExport e = exportRepository.findByIdAndUserId(id, userId)
                    .orElseThrow(() -> ApiException.notFound("Export"));
            if (e.getStatus() != ProcessingStatus.COMPLETED || e.getStorageKey() == null) {
                throw ApiException.conflict("EXPORT_NOT_READY", "This export has no file");
            }
            String base = e.getResumeVersion().getResume().getTitle().replaceAll("[^\\p{L}\\p{N}._-]+", "_");
            return new Meta(e.getStorageKey(), base + "-v" + e.getResumeVersion().getVersionNumber() + ".pdf",
                    e.getFileSizeBytes());
        });
        try {
            return new PdfFile(storage.open(meta.key()), meta.filename(), meta.size() == null ? -1 : meta.size());
        } catch (IOException ex) {
            throw ApiException.notFound("Export file");
        }
    }

    public void delete(Long userId, Long id) {
        String key = tx.execute(s -> {
            PdfExport e = exportRepository.findByIdAndUserId(id, userId)
                    .orElseThrow(() -> ApiException.notFound("Export"));
            exportRepository.delete(e);
            return e.getStorageKey();
        });
        storage.delete(key);
    }
}
