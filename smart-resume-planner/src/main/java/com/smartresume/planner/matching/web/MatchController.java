package com.smartresume.planner.matching.web;

import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.matching.dto.MatchingDtos.MatchRequest;
import com.smartresume.planner.matching.dto.MatchingDtos.MatchResponse;
import com.smartresume.planner.matching.dto.MatchingDtos.MatchSummary;
import com.smartresume.planner.matching.service.MatchService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
@Tag(name = "Job matcher and skill gaps")
public class MatchController {

    private final MatchService service;

    @Operation(summary = "Match a resume version against a job description")
    @PostMapping
    public ResponseEntity<MatchResponse> run(@Valid @RequestBody MatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.run(CurrentUser.id(), request));
    }

    @GetMapping("/{id}")
    public MatchResponse get(@PathVariable Long id) {
        return service.get(CurrentUser.id(), id);
    }

    @Operation(summary = "Match history, optionally filtered by job or resume")
    @GetMapping
    public PagedResponse<MatchSummary> list(@RequestParam(required = false) Long jobDescriptionId,
                                            @RequestParam(required = false) Long resumeId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(CurrentUser.id(), jobDescriptionId, resumeId, page, size);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
