package com.smartresume.planner.github.service;

import com.smartresume.planner.ats.dto.AtsResult;
import com.smartresume.planner.ats.service.AtsService;
import com.smartresume.planner.common.config.AppProperties;
import com.smartresume.planner.common.domain.AsyncJob;
import com.smartresume.planner.common.domain.AsyncJobType;
import com.smartresume.planner.common.domain.ProcessingStatus;
import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.common.repository.AsyncJobRepository;
import com.smartresume.planner.common.util.Texts;
import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.github.domain.GithubAnalysis;
import com.smartresume.planner.github.domain.GithubRepository;
import com.smartresume.planner.github.dto.GithubDtos.AnalysisResponse;
import com.smartresume.planner.github.dto.GithubDtos.JobResponse;
import com.smartresume.planner.github.dto.GithubDtos.RepoResponse;
import com.smartresume.planner.github.dto.GithubDtos.StartResponse;
import com.smartresume.planner.github.dto.GithubDtos.ToResumeRequest;
import com.smartresume.planner.github.dto.GithubDtos.UpdateRepoRequest;
import com.smartresume.planner.github.repository.GithubAnalysisRepository;
import com.smartresume.planner.resume.domain.Resume;
import com.smartresume.planner.resume.domain.ResumeDocument;
import com.smartresume.planner.resume.domain.ResumeDocument.Project;
import com.smartresume.planner.resume.domain.ResumeVersion;
import com.smartresume.planner.resume.domain.VersionSource;
import com.smartresume.planner.resume.dto.ResumeDtos.VersionDetail;
import com.smartresume.planner.resume.parse.DocumentNormalizer;
import com.smartresume.planner.resume.parse.ResumeParser;
import com.smartresume.planner.resume.parse.ResumeTextRenderer;
import com.smartresume.planner.resume.repository.ResumeRepository;
import com.smartresume.planner.resume.repository.ResumeVersionRepository;
import com.smartresume.planner.user.domain.User;
import com.smartresume.planner.user.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Feature 2: GitHub project analyzer. Analyses run in the background; the client polls the job. */
@Slf4j
@Service
@RequiredArgsConstructor
public class GithubService {

    private final GithubAnalysisRepository analysisRepository;
    private final AsyncJobRepository jobRepository;
    private final UserRepository userRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository versionRepository;
    private final GithubAnalysisRunner runner;
    private final AtsService atsService;
    private final ResumeParser parser;
    private final AppProperties props;
    private final TransactionTemplate tx;

    /** Starts (or reuses) an analysis. Returns 'cached' when a fresh completed one exists and refresh is not forced. */
    public StartResponse start(Long userId, String requestedUsername, boolean refresh) {
        record Prepared(StartResponse response, String username, boolean launch) { }
        Prepared p = tx.execute(s -> {
            User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
            String username = Texts.blankToNull(requestedUsername) != null ? requestedUsername.trim() : user.getGithubUsername();
            if (username == null) {
                throw ApiException.badRequest("GITHUB_USERNAME_REQUIRED",
                        "Provide a GitHub username, or save one in your profile first");
            }
            if (!refresh) {
                var fresh = analysisRepository
                        .findFirstByUserIdAndGithubUsernameIgnoreCaseAndStatusAndAnalyzedAtAfterOrderByIdDesc(userId,
                                username, ProcessingStatus.COMPLETED, Instant.now().minus(props.github().cacheTtl()));
                if (fresh.isPresent()) {
                    return new Prepared(new StartResponse(null, fresh.get().getId(), ProcessingStatus.COMPLETED, true),
                            username, false);
                }
            }
            GithubAnalysis analysis = analysisRepository.saveAndFlush(new GithubAnalysis(user, username));
            AsyncJob job = jobRepository.saveAndFlush(new AsyncJob(user, AsyncJobType.GITHUB_ANALYSIS));
            return new Prepared(new StartResponse(job.getId(), analysis.getId(), ProcessingStatus.PENDING, false),
                    username, true);
        });
        if (p.launch()) {
            // Runs after the transaction above has committed, so the async thread can see both rows.
            runner.run(p.response().jobId(), p.response().analysisId(), userId, p.username());
        }
        return p.response();
    }

    @Transactional(readOnly = true)
    public JobResponse job(Long userId, UUID jobId) {
        return JobResponse.from(jobRepository.findOwned(jobId, userId).orElseThrow(() -> ApiException.notFound("Job")));
    }

    @Transactional(readOnly = true)
    public PagedResponse<AnalysisResponse> list(Long userId, int page, int size) {
        return PagedResponse.of(analysisRepository.findByUserId(userId, PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), 50), Sort.by(Sort.Direction.DESC, "createdAt"))),
                AnalysisResponse::summary);
    }

    @Transactional(readOnly = true)
    public AnalysisResponse get(Long userId, Long id) {
        return AnalysisResponse.detail(analysisRepository.findWithRepositoriesByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("GitHub analysis")));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        analysisRepository.delete(analysisRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("GitHub analysis")));
    }

    @Transactional
    public RepoResponse updateRepo(Long userId, Long analysisId, Long repoId, UpdateRepoRequest request) {
        GithubRepository repo = findRepo(userId, analysisId, repoId);
        repo.setIncludedInResume(request.includedInResume());
        if (request.suggestedBullet() != null && !request.suggestedBullet().isBlank()) {
            repo.setSuggestedBullet(request.suggestedBullet().strip());
        }
        return RepoResponse.from(repo);
    }

    /** Adds the chosen repositories as "Projects" in a new IMPORT version of the resume. */
    public VersionDetail addToResume(Long userId, Long analysisId, ToResumeRequest request) {
        return tx.execute(s -> {
            GithubAnalysis analysis = analysisRepository.findWithRepositoriesByIdAndUserId(analysisId, userId)
                    .orElseThrow(() -> ApiException.notFound("GitHub analysis"));
            Resume resume = resumeRepository.findByIdAndUserId(request.resumeId(), userId)
                    .orElseThrow(() -> ApiException.notFound("Resume"));
            ResumeVersion parent = resume.getCurrentVersion();
            if (parent == null) {
                throw ApiException.notFound("Resume version");
            }
            List<GithubRepository> chosen = analysis.getRepositories().stream()
                    .filter(r -> request.repositoryIds().contains(r.getId())).toList();
            if (chosen.size() != request.repositoryIds().stream().distinct().count()) {
                throw ApiException.notFound("Repository");
            }

            ResumeDocument base = DocumentNormalizer.normalize(parent.getStructuredContent() != null
                    ? parent.getStructuredContent() : parser.parse(parent.getExtractedText()).document());
            List<Project> projects = new ArrayList<>(base.projects());
            List<String> existing = projects.stream().map(p -> p.name().toLowerCase(Locale.ROOT)).toList();
            int added = 0;
            for (GithubRepository r : chosen) {
                if (existing.contains(r.getRepoName().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                List<String> bullets = r.getSuggestedBullet() == null ? List.of() : List.of(r.getSuggestedBullet());
                List<String> tech = r.getDetectedTech() == null ? List.of() : r.getDetectedTech().stream().limit(8).toList();
                projects.add(new Project(r.getRepoName(), r.getRepoUrl(), "", bullets, tech));
                r.setIncludedInResume(true);
                added++;
            }
            if (added == 0) {
                throw ApiException.conflict("NOTHING_TO_ADD", "Those repositories are already on this resume");
            }
            ResumeDocument edited = new ResumeDocument(base.contact(), base.summary(), base.experience(),
                    base.education(), base.skills(), projects, base.certifications());
            String text = ResumeTextRenderer.toPlainText(edited);
            ResumeVersion version = ResumeVersion.derivedFrom(parent, VersionSource.IMPORT,
                    Texts.blankToNull(request.label()) != null ? request.label().trim()
                            : "Added " + added + " GitHub project" + (added == 1 ? "" : "s"), text, edited);
            resume.addVersion(version);
            versionRepository.saveAndFlush(version);
            AtsResult ats = AtsResult.from(atsService.analyzeAndSave(version));
            return VersionDetail.of(version, resume.getId(), true, ats);
        });
    }

    private GithubRepository findRepo(Long userId, Long analysisId, Long repoId) {
        GithubAnalysis analysis = analysisRepository.findWithRepositoriesByIdAndUserId(analysisId, userId)
                .orElseThrow(() -> ApiException.notFound("GitHub analysis"));
        return analysis.getRepositories().stream().filter(r -> r.getId().equals(repoId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Repository"));
    }
}
