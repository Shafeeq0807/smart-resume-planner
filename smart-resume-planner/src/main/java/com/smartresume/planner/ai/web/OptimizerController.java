package com.smartresume.planner.ai.web;

import com.smartresume.planner.ai.domain.GeneratedDocumentType;
import com.smartresume.planner.ai.dto.OptimizerDtos.AnalyzeBulletsRequest;
import com.smartresume.planner.ai.dto.OptimizerDtos.AnalyzeBulletsResponse;
import com.smartresume.planner.ai.dto.OptimizerDtos.ApplyRewritesRequest;
import com.smartresume.planner.ai.dto.OptimizerDtos.BulletRewriteResponse;
import com.smartresume.planner.ai.dto.OptimizerDtos.CoverLetterRequest;
import com.smartresume.planner.ai.dto.OptimizerDtos.DocumentResponse;
import com.smartresume.planner.ai.dto.OptimizerDtos.DocumentSummary;
import com.smartresume.planner.ai.dto.OptimizerDtos.InterviewQuestionsRequest;
import com.smartresume.planner.ai.dto.OptimizerDtos.UpdateDocumentRequest;
import com.smartresume.planner.ai.service.BulletOptimizerService;
import com.smartresume.planner.ai.service.DocumentGenerationService;
import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.resume.dto.ResumeDtos.VersionDetail;
import com.smartresume.planner.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "AI optimizer")
public class OptimizerController {

    private final BulletOptimizerService bullets;
    private final DocumentGenerationService documents;

    @Operation(summary = "Find weak bullets and generate impact rewrites (XYZ format)")
    @PostMapping("/optimizer/bullets/analyze")
    public AnalyzeBulletsResponse analyze(@Valid @RequestBody AnalyzeBulletsRequest request) {
        return bullets.analyze(CurrentUser.id(), request);
    }

    @Operation(summary = "Saved rewrite suggestions for a resume version")
    @GetMapping("/optimizer/bullets")
    public List<BulletRewriteResponse> listRewrites(@RequestParam Long resumeId,
                                                    @RequestParam(required = false) Long versionId) {
        return bullets.list(CurrentUser.id(), resumeId, versionId);
    }

    @Operation(summary = "Accept rewrites: creates a new AI_REWRITE version of the resume")
    @PostMapping("/optimizer/bullets/apply")
    public ResponseEntity<VersionDetail> apply(@Valid @RequestBody ApplyRewritesRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bullets.apply(CurrentUser.id(), request));
    }

    @PostMapping("/optimizer/bullets/{id}/reject")
    public BulletRewriteResponse reject(@PathVariable Long id) {
        return bullets.reject(CurrentUser.id(), id);
    }

    @Operation(summary = "Generate a cover letter tailored to a job")
    @PostMapping("/optimizer/cover-letters")
    public ResponseEntity<DocumentResponse> coverLetter(@Valid @RequestBody CoverLetterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documents.coverLetter(CurrentUser.id(), request));
    }

    @Operation(summary = "Generate interview practice questions tailored to a job and resume")
    @PostMapping("/optimizer/interview-questions")
    public ResponseEntity<DocumentResponse> interviewQuestions(@Valid @RequestBody InterviewQuestionsRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documents.interviewQuestions(CurrentUser.id(), request));
    }

    // ---- saved generated documents ----

    @GetMapping("/documents")
    public PagedResponse<DocumentSummary> listDocuments(@RequestParam(required = false) GeneratedDocumentType type,
                                                        @RequestParam(required = false) Long applicationId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return documents.list(CurrentUser.id(), type, applicationId, page, size);
    }

    @GetMapping("/documents/{id}")
    public DocumentResponse getDocument(@PathVariable Long id) {
        return documents.get(CurrentUser.id(), id);
    }

    @PutMapping("/documents/{id}")
    public DocumentResponse updateDocument(@PathVariable Long id, @Valid @RequestBody UpdateDocumentRequest request) {
        return documents.update(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        documents.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
