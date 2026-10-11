package com.smartresume.planner.resume.web;

import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.resume.dto.ResumeDtos.CompareResponse;
import com.smartresume.planner.resume.dto.ResumeDtos.EditVersionRequest;
import com.smartresume.planner.resume.dto.ResumeDtos.ResumeDetail;
import com.smartresume.planner.resume.dto.ResumeDtos.ResumeSummary;
import com.smartresume.planner.resume.dto.ResumeDtos.UpdateResumeRequest;
import com.smartresume.planner.resume.dto.ResumeDtos.VersionDetail;
import com.smartresume.planner.resume.dto.ResumeDtos.VersionSummary;
import com.smartresume.planner.resume.service.ResumeService;
import com.smartresume.planner.resume.service.ResumeService.Download;
import com.smartresume.planner.resume.service.VersionDiffService;
import com.smartresume.planner.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
@Validated
@Tag(name = "Resumes and versions")
public class ResumeController {

    private final ResumeService resumeService;
    private final VersionDiffService diffService;

    @Operation(summary = "Upload a PDF/DOCX (max 10 MB) as a new resume; returns v1 with its ATS analysis")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeDetail> upload(@RequestPart("file") MultipartFile file,
                                               @RequestParam(required = false) @Size(max = 150) String title,
                                               @RequestParam(required = false) @Size(max = 150) String targetRole) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resumeService.create(CurrentUser.id(), file, title, targetRole));
    }

    @Operation(summary = "List my resumes")
    @GetMapping
    public PagedResponse<ResumeSummary> list(@RequestParam(defaultValue = "false") boolean includeArchived,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return resumeService.list(CurrentUser.id(), includeArchived, page, size);
    }

    @GetMapping("/{resumeId}")
    public ResumeDetail get(@PathVariable Long resumeId) {
        return resumeService.detail(CurrentUser.id(), resumeId);
    }

    @Operation(summary = "Rename, change target role, archive/unarchive")
    @PatchMapping("/{resumeId}")
    public ResumeSummary update(@PathVariable Long resumeId, @Valid @RequestBody UpdateResumeRequest request) {
        return resumeService.update(CurrentUser.id(), resumeId, request);
    }

    @DeleteMapping("/{resumeId}")
    public ResponseEntity<Void> delete(@PathVariable Long resumeId) {
        resumeService.delete(CurrentUser.id(), resumeId);
        return ResponseEntity.noContent().build();
    }

    // ---- versions ----

    @GetMapping("/{resumeId}/versions")
    public List<VersionSummary> versions(@PathVariable Long resumeId) {
        return resumeService.versions(CurrentUser.id(), resumeId);
    }

    @GetMapping("/{resumeId}/versions/{versionId}")
    public VersionDetail version(@PathVariable Long resumeId, @PathVariable Long versionId) {
        return resumeService.versionDetail(CurrentUser.id(), resumeId, versionId);
    }

    @Operation(summary = "Upload a new file as the next version (v2, v3...)")
    @PostMapping(value = "/{resumeId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VersionDetail> uploadVersion(@PathVariable Long resumeId,
                                                       @RequestPart("file") MultipartFile file,
                                                       @RequestParam(required = false) @Size(max = 100) String label) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resumeService.addUploadedVersion(CurrentUser.id(), resumeId, file, label));
    }

    @Operation(summary = "Save edited text or a structured document as a new version")
    @PostMapping("/{resumeId}/versions/edit")
    public ResponseEntity<VersionDetail> edit(@PathVariable Long resumeId,
                                              @Valid @RequestBody EditVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resumeService.addEditedVersion(CurrentUser.id(), resumeId, request));
    }

    @Operation(summary = "Make an older version the current one")
    @PostMapping("/{resumeId}/versions/{versionId}/activate")
    public VersionDetail activate(@PathVariable Long resumeId, @PathVariable Long versionId) {
        return resumeService.activateVersion(CurrentUser.id(), resumeId, versionId);
    }

    @Operation(summary = "Side-by-side diff between two versions")
    @GetMapping("/{resumeId}/compare")
    public CompareResponse compare(@PathVariable Long resumeId, @RequestParam Long from, @RequestParam Long to) {
        return diffService.compare(CurrentUser.id(), resumeId, from, to);
    }

    @Operation(summary = "Download the originally uploaded file of a version")
    @GetMapping("/{resumeId}/versions/{versionId}/file")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long resumeId, @PathVariable Long versionId) {
        Download d = resumeService.openOriginal(CurrentUser.id(), resumeId, versionId);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.filename()).build().toString());
        if (d.size() >= 0) {
            response.contentLength(d.size());
        }
        return response.body(new InputStreamResource(d.stream()));
    }
}
