package com.smartresume.planner.ats.web;

import com.smartresume.planner.ats.dto.AtsResult;
import com.smartresume.planner.ats.service.AtsService;
import com.smartresume.planner.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumes/{resumeId}/versions/{versionId}/ats")
@RequiredArgsConstructor
@Tag(name = "ATS evaluation")
public class AtsController {

    private final AtsService atsService;

    @Operation(summary = "Latest ATS analysis of a resume version")
    @GetMapping
    public AtsResult latest(@PathVariable Long resumeId, @PathVariable Long versionId) {
        return atsService.latest(CurrentUser.id(), resumeId, versionId);
    }

    @Operation(summary = "All past ATS analyses of a version, newest first")
    @GetMapping("/history")
    public List<AtsResult> history(@PathVariable Long resumeId, @PathVariable Long versionId) {
        return atsService.history(CurrentUser.id(), resumeId, versionId);
    }

    @Operation(summary = "Re-run the ATS analysis; ?ai=true adds an AI coaching summary")
    @PostMapping
    public AtsResult rerun(@PathVariable Long resumeId, @PathVariable Long versionId,
                           @RequestParam(defaultValue = "false") boolean ai) {
        return atsService.rerun(CurrentUser.id(), resumeId, versionId, ai);
    }
}
