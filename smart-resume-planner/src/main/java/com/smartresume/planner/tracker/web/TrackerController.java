package com.smartresume.planner.tracker.web;

import com.smartresume.planner.security.CurrentUser;
import com.smartresume.planner.tracker.dto.TrackerDtos.ApplicationResponse;
import com.smartresume.planner.tracker.dto.TrackerDtos.Board;
import com.smartresume.planner.tracker.dto.TrackerDtos.CreateApplicationRequest;
import com.smartresume.planner.tracker.dto.TrackerDtos.FromJobRequest;
import com.smartresume.planner.tracker.dto.TrackerDtos.MoveRequest;
import com.smartresume.planner.tracker.dto.TrackerDtos.Stats;
import com.smartresume.planner.tracker.dto.TrackerDtos.UpdateApplicationRequest;
import com.smartresume.planner.tracker.service.TrackerService;
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
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Tag(name = "Application tracker")
public class TrackerController {

    private final TrackerService service;

    @Operation(summary = "Kanban board: one column per status, cards in order")
    @GetMapping("/board")
    public Board board() {
        return service.board(CurrentUser.id());
    }

    @GetMapping("/stats")
    public Stats stats() {
        return service.stats(CurrentUser.id());
    }

    @Operation(summary = "Applications whose follow-up date is due within N days (or overdue)")
    @GetMapping("/follow-ups")
    public List<ApplicationResponse> followUps(@RequestParam(defaultValue = "7") int days) {
        return service.followUps(CurrentUser.id(), days);
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(CurrentUser.id(), request));
    }

    @Operation(summary = "Create a SAVED application from a saved job description")
    @PostMapping("/from-job/{jobDescriptionId}")
    public ResponseEntity<ApplicationResponse> fromJob(@PathVariable Long jobDescriptionId,
                                                       @RequestBody(required = false) FromJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createFromJob(CurrentUser.id(), jobDescriptionId, request));
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable Long id) {
        return service.get(CurrentUser.id(), id);
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@PathVariable Long id, @Valid @RequestBody UpdateApplicationRequest request) {
        return service.update(CurrentUser.id(), id, request);
    }

    @Operation(summary = "Move a card to a column and position (drag and drop)")
    @PostMapping("/{id}/move")
    public ApplicationResponse move(@PathVariable Long id, @Valid @RequestBody MoveRequest request) {
        return service.move(CurrentUser.id(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
