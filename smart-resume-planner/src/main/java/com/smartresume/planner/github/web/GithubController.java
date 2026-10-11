package com.smartresume.planner.github.web;

import com.smartresume.planner.common.domain.ProcessingStatus;
import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.github.dto.GithubDtos.AnalysisResponse;
import com.smartresume.planner.github.dto.GithubDtos.AnalyzeRequest;
import com.smartresume.planner.github.dto.GithubDtos.JobResponse;
import com.smartresume.planner.github.dto.GithubDtos.RepoResponse;
import com.smartresume.planner.github.dto.GithubDtos.StartResponse;
import com.smartresume.planner.github.dto.GithubDtos.ToResumeRequest;
import com.smartresume.planner.github.dto.GithubDtos.UpdateRepoRequest;
import com.smartresume.planner.github.service.GithubService;
import com.smartresume.planner.resume.dto.ResumeDtos.VersionDetail;
import com.smartresume.planner.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "GitHub project analyzer")
public class GithubController {

    private final GithubService service;

    @Operation(summary = "Start analysing a GitHub profile (202 + job id), or reuse a result from the last 24 h (200)")
    @PostMapping("/github/analyses")
    public ResponseEntity<StartResponse> start(@Valid @RequestBody(required = false) AnalyzeRequest request) {
        String username = request == null ? null : request.username();
        boolean refresh = request != null && Boolean.TRUE.equals(request.refresh());
        StartResponse response = service.start(CurrentUser.id(), username, refresh);
        return ResponseEntity.status(response.cached() || response.status() == ProcessingStatus.COMPLETED
                ? HttpStatus.OK : HttpStatus.ACCEPTED).body(response);
    }

    @Operation(summary = "Poll a background job")
    @GetMapping("/jobs/{jobId}")
    public JobResponse job(@PathVariable UUID jobId) {
        return service.job(CurrentUser.id(), jobId);
    }

    @GetMapping("/github/analyses")
    public PagedResponse<AnalysisResponse> list(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "10") int size) {
        return service.list(CurrentUser.id(), page, size);
    }

    @GetMapping("/github/analyses/{id}")
    public AnalysisResponse get(@PathVariable Long id) {
        return service.get(CurrentUser.id(), id);
    }

    @DeleteMapping("/github/analyses/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Mark a repo as included, or edit its suggested resume bullet")
    @PatchMapping("/github/analyses/{analysisId}/repositories/{repoId}")
    public RepoResponse updateRepo(@PathVariable Long analysisId, @PathVariable Long repoId,
                                   @Valid @RequestBody UpdateRepoRequest request) {
        return service.updateRepo(CurrentUser.id(), analysisId, repoId, request);
    }

    @Operation(summary = "Add selected repositories to a resume as Projects (creates a new version)")
    @PostMapping("/github/analyses/{analysisId}/to-resume")
    public ResponseEntity<VersionDetail> toResume(@PathVariable Long analysisId,
                                                  @Valid @RequestBody ToResumeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addToResume(CurrentUser.id(), analysisId, request));
    }
}
