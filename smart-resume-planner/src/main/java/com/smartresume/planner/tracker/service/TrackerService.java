package com.smartresume.planner.tracker.service;

import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.common.util.Texts;
import com.smartresume.planner.matching.domain.JobDescription;
import com.smartresume.planner.matching.repository.JobDescriptionRepository;
import com.smartresume.planner.resume.repository.ResumeVersionRepository;
import com.smartresume.planner.tracker.domain.ApplicationStatus;
import com.smartresume.planner.tracker.domain.JobApplication;
import com.smartresume.planner.tracker.dto.TrackerDtos.ApplicationResponse;
import com.smartresume.planner.tracker.dto.TrackerDtos.Board;
import com.smartresume.planner.tracker.dto.TrackerDtos.Column;
import com.smartresume.planner.tracker.dto.TrackerDtos.CreateApplicationRequest;
import com.smartresume.planner.tracker.dto.TrackerDtos.FromJobRequest;
import com.smartresume.planner.tracker.dto.TrackerDtos.MoveRequest;
import com.smartresume.planner.tracker.dto.TrackerDtos.Stats;
import com.smartresume.planner.tracker.dto.TrackerDtos.UpdateApplicationRequest;
import com.smartresume.planner.tracker.repository.JobApplicationRepository;
import com.smartresume.planner.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kanban pipeline: Saved -> Applied -> Interviewing -> Offered / Rejected, with fractional ordering inside a column. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TrackerService {

    private static final double STEP = 1024.0;
    private static final List<ApplicationStatus> ACTIVE = List.of(ApplicationStatus.SAVED, ApplicationStatus.APPLIED,
            ApplicationStatus.INTERVIEWING);

    private final JobApplicationRepository repository;
    private final UserRepository userRepository;
    private final JobDescriptionRepository jobRepository;
    private final ResumeVersionRepository versionRepository;

    @Transactional(readOnly = true)
    public Board board(Long userId) {
        Map<ApplicationStatus, List<ApplicationResponse>> grouped = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            grouped.put(s, new ArrayList<>());
        }
        int total = 0;
        for (JobApplication a : repository.findByUserIdOrderByStatusAscBoardOrderAsc(userId)) {
            grouped.get(a.getStatus()).add(ApplicationResponse.card(a));
            total++;
        }
        List<Column> columns = new ArrayList<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            columns.add(new Column(s, grouped.get(s).size(), grouped.get(s)));
        }
        return new Board(columns, total);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(Long userId, Long id) {
        return ApplicationResponse.detail(repository.findWithHistoryByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Application")));
    }

    public ApplicationResponse create(Long userId, CreateApplicationRequest r) {
        ApplicationStatus status = r.status() == null ? ApplicationStatus.SAVED : r.status();
        double order = repository.maxOrder(userId, status).orElse(0.0) + STEP;
        JobApplication a = new JobApplication(userRepository.getReferenceById(userId), r.companyName().trim(),
                r.positionTitle().trim(), order);
        a.setJobUrl(Texts.blankToNull(r.jobUrl()));
        a.setLocation(Texts.blankToNull(r.location()));
        a.setSalaryRange(Texts.blankToNull(r.salaryRange()));
        a.setNotes(Texts.blankToNull(r.notes()));
        a.setNextFollowUpAt(r.nextFollowUpAt());
        a.setAppliedAt(r.appliedAt());
        link(userId, a, r.jobDescriptionId(), r.resumeVersionId());
        if (status != ApplicationStatus.SAVED) {
            a.moveTo(status, order, "Created as " + status);
        }
        repository.saveAndFlush(a);
        log.info("Created application id={} for user id={}", a.getId(), userId);
        return ApplicationResponse.detail(a);
    }

    /** One click from a saved job description into the pipeline. */
    public ApplicationResponse createFromJob(Long userId, Long jobDescriptionId, FromJobRequest r) {
        JobDescription jd = jobRepository.findByIdAndUserId(jobDescriptionId, userId)
                .orElseThrow(() -> ApiException.notFound("Job description"));
        return create(userId, new CreateApplicationRequest(
                jd.getCompany() == null ? "Unknown company" : jd.getCompany(), jd.getTitle(), jd.getSourceUrl(),
                jd.getLocation(), null, r == null ? null : r.status(), jd.getId(),
                r == null ? null : r.resumeVersionId(), null, null, null));
    }

    public ApplicationResponse update(Long userId, Long id, UpdateApplicationRequest r) {
        JobApplication a = repository.findWithHistoryByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Application"));
        checkVersion(a, r.version());
        a.setCompanyName(r.companyName().trim());
        a.setPositionTitle(r.positionTitle().trim());
        a.setJobUrl(Texts.blankToNull(r.jobUrl()));
        a.setLocation(Texts.blankToNull(r.location()));
        a.setSalaryRange(Texts.blankToNull(r.salaryRange()));
        a.setNotes(Texts.blankToNull(r.notes()));
        a.setAppliedAt(r.appliedAt());
        a.setNextFollowUpAt(r.nextFollowUpAt());
        link(userId, a, r.jobDescriptionId(), r.resumeVersionId());
        repository.saveAndFlush(a);
        return ApplicationResponse.detail(a);
    }

    /** Moves a card to a column and position. Concurrent drags of the same card fail with 409 (optimistic lock). */
    public ApplicationResponse move(Long userId, Long id, MoveRequest r) {
        JobApplication a = repository.findWithHistoryByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Application"));
        checkVersion(a, r.version());

        List<JobApplication> column = new ArrayList<>(repository.findByUserIdAndStatusOrderByBoardOrderAsc(userId, r.status()));
        column.removeIf(x -> x.getId().equals(a.getId()));
        int pos = Math.min(r.position(), column.size());
        Double prev = pos > 0 ? column.get(pos - 1).getBoardOrder() : null;
        Double next = pos < column.size() ? column.get(pos).getBoardOrder() : null;

        double order;
        if (prev == null && next == null) {
            order = STEP;
        } else if (prev == null) {
            order = next - STEP;
        } else if (next == null) {
            order = prev + STEP;
        } else if (next - prev < 1e-6) {
            renumber(column, a, pos);
            order = (pos + 1) * STEP;
        } else {
            order = (prev + next) / 2.0;
        }
        a.moveTo(r.status(), order, Texts.blankToNull(r.note()));
        repository.saveAndFlush(a);
        return ApplicationResponse.detail(a);
    }

    public void delete(Long userId, Long id) {
        repository.delete(repository.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Application")));
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> followUps(Long userId, int days) {
        Instant until = Instant.now().plus(Duration.ofDays(Math.max(0, Math.min(days, 90))));
        return repository.findFollowUps(userId, until, ACTIVE).stream().map(ApplicationResponse::card).toList();
    }

    @Transactional(readOnly = true)
    public Stats stats(Long userId) {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            counts.put(s, 0L);
        }
        for (Object[] row : repository.countByStatus(userId)) {
            counts.put((ApplicationStatus) row[0], (Long) row[1]);
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long active = ACTIVE.stream().mapToLong(counts::get).sum();
        long applied = total - counts.get(ApplicationStatus.SAVED);
        long interviewing = counts.get(ApplicationStatus.INTERVIEWING) + counts.get(ApplicationStatus.OFFERED);
        double interviewRate = applied == 0 ? 0 : round1(100.0 * interviewing / applied);
        double offerRate = applied == 0 ? 0 : round1(100.0 * counts.get(ApplicationStatus.OFFERED) / applied);
        return new Stats(counts, total, active, interviewRate, offerRate);
    }

    // ------------------------------------------------------------------ helpers

    private void renumber(List<JobApplication> column, JobApplication moving, int gapAt) {
        double order = STEP;
        for (int i = 0; i < column.size(); i++) {
            if (i == gapAt) {
                order += STEP;      // leave a slot for the moving card
            }
            column.get(i).moveTo(column.get(i).getStatus(), order, null);
            order += STEP;
        }
    }

    private void link(Long userId, JobApplication a, Long jobDescriptionId, Long resumeVersionId) {
        a.setJobDescription(jobDescriptionId == null ? null : jobRepository.findByIdAndUserId(jobDescriptionId, userId)
                .orElseThrow(() -> ApiException.notFound("Job description")));
        a.setResumeVersion(resumeVersionId == null ? null : versionRepository.findOwnedByUser(resumeVersionId, userId)
                .orElseThrow(() -> ApiException.notFound("Resume version")));
    }

    private static void checkVersion(JobApplication a, Long expected) {
        if (expected != null && !expected.equals(a.getVersion())) {
            throw ApiException.conflict("CONCURRENT_MODIFICATION",
                    "This application was changed elsewhere. Reload the board and try again.");
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
