package com.smartresume.planner.matching.web;

import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.matching.dto.MatchingDtos.JobDescriptionRequest;
import com.smartresume.planner.matching.dto.MatchingDtos.JobDescriptionResponse;
import com.smartresume.planner.matching.dto.MatchingDtos.JobDescriptionSummary;
import com.smartresume.planner.matching.service.JobDescriptionService;
import com.smartresume.planner.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/job-descriptions")
@RequiredArgsConstructor
@Tag(name = "Job descriptions")
public class JobDescriptionController {

    private final JobDescriptionService service;

    @Operation(summary = "Save a job description; required and preferred skills are extracted automatically")
    @PostMapping
    public ResponseEntity<JobDescriptionResponse> create(@Valid @RequestBody JobDescriptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(CurrentUser.id(), request));
    }

    @GetMapping
    public PagedResponse<JobDescriptionSummary> list(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.id(), page, size);
    }

    @GetMapping("/{id}")
    public JobDescriptionResponse get(@PathVariable Long id) {
        return service.get(CurrentUser.id(), id);
    }

    @PutMapping("/{id}")
    public JobDescriptionResponse update(@PathVariable Long id, @Valid @RequestBody JobDescriptionRequest request) {
        return service.update(CurrentUser.id(), id, request);
    }

    @Operation(summary = "Run skill extraction again")
    @PostMapping("/{id}/re-extract")
    public JobDescriptionResponse reextract(@PathVariable Long id) {
        return service.reextract(CurrentUser.id(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
