package com.smartresume.planner.export.web;

import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.export.dto.ExportDtos.ExportRequest;
import com.smartresume.planner.export.dto.ExportDtos.ExportResponse;
import com.smartresume.planner.export.dto.ExportDtos.TemplateResponse;
import com.smartresume.planner.export.service.ExportService;
import com.smartresume.planner.export.service.ExportService.PdfFile;
import com.smartresume.planner.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "PDF resume exporter")
public class ExportController {

    private final ExportService service;

    @GetMapping("/templates")
    public List<TemplateResponse> templates() {
        return service.templates();
    }

    @Operation(summary = "Render a resume version to PDF with a template and style settings")
    @PostMapping("/exports")
    public ResponseEntity<ExportResponse> export(@Valid @RequestBody ExportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.export(CurrentUser.id(), request));
    }

    @GetMapping("/exports")
    public PagedResponse<ExportResponse> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.id(), page, size);
    }

    @GetMapping("/exports/{id}")
    public ExportResponse get(@PathVariable Long id) {
        return service.get(CurrentUser.id(), id);
    }

    @GetMapping("/exports/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        PdfFile file = service.open(CurrentUser.id(), id);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(file.filename()).build().toString());
        if (file.size() >= 0) {
            response.contentLength(file.size());
        }
        return response.body(new InputStreamResource(file.stream()));
    }

    @DeleteMapping("/exports/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
