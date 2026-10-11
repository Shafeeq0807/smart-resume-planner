package com.smartresume.planner.matching.service;

import com.smartresume.planner.ai.service.AiGateway;
import com.smartresume.planner.ai.service.AiPrompts;
import com.smartresume.planner.common.domain.ProcessingStatus;
import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.github.repository.GithubAnalysisRepository;
import com.smartresume.planner.matching.domain.JobDescription;
import com.smartresume.planner.matching.domain.JobMatchAnalysis;
import com.smartresume.planner.matching.domain.JobRequirements;
import com.smartresume.planner.matching.domain.SkillGap;
import com.smartresume.planner.matching.dto.MatchingDtos.MatchRequest;
import com.smartresume.planner.matching.dto.MatchingDtos.MatchResponse;
import com.smartresume.planner.matching.dto.MatchingDtos.MatchSummary;
import com.smartresume.planner.matching.repository.JobDescriptionRepository;
import com.smartresume.planner.matching.repository.JobMatchAnalysisRepository;
import com.smartresume.planner.matching.service.MatchEngine.Result;
import com.smartresume.planner.resume.repository.ResumeVersionRepository;
import com.smartresume.planner.resume.service.ResumeLookup;
import com.smartresume.planner.resume.service.ResumeLookup.Snapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final ResumeLookup resumeLookup;
    private final JobDescriptionRepository jobRepository;
    private final JobMatchAnalysisRepository matchRepository;
    private final ResumeVersionRepository versionRepository;
    private final GithubAnalysisRepository githubRepository;
    private final MatchEngine engine;
    private final AiGateway ai;
    private final TransactionTemplate tx;

    private record JobData(Long id, String title, String text, JobRequirements requirements) { }

    /** Scores a resume version against a job description and stores the result with its skill gaps. */
    public MatchResponse run(Long userId, MatchRequest request) {
        Snapshot resume = resumeLookup.snapshot(userId, request.resumeId(), request.versionId());
        JobData job = tx.execute(s -> {
            JobDescription jd = jobRepository.findByIdAndUserId(request.jobDescriptionId(), userId)
                    .orElseThrow(() -> ApiException.notFound("Job description"));
            if (jd.getExtractedSkills() == null) {
                throw ApiException.conflict("JOB_NOT_ANALYZED",
                        "This job description has no extracted skills yet. Call re-extract first.");
            }
            return new JobData(jd.getId(), jd.getTitle(), jd.getRawText(), jd.getExtractedSkills());
        });
        Set<String> githubSkills = request.includeGithub() == null || request.includeGithub()
                ? githubSkills(userId) : Set.of();

        Result result = engine.compute(new MatchEngine.Input(resume.text(), resume.document(), resume.targetRole(),
                githubSkills, job.title(), job.text(), job.requirements()));

        List<String> recommendations = result.recommendations();
        String modelName = null;
        if (request.includeAi() == null || request.includeAi()) {
            List<String> aiRecs = aiRecommendations(userId, resume, job, result);
            if (!aiRecs.isEmpty()) {
                recommendations = aiRecs;
                modelName = ai.provider();
            }
        }

        List<String> finalRecs = recommendations;
        String finalModel = modelName;
        return tx.execute(s -> {
            JobMatchAnalysis m = new JobMatchAnalysis(versionRepository.getReferenceById(resume.versionId()),
                    jobRepository.getReferenceById(job.id()), MatchEngine.VERSION);
            m.setMatchPercentage(result.match());
            m.setHardSkillCoverage(result.hardCoverage());
            m.setSoftSkillCoverage(result.softCoverage());
            m.setSemanticScore(result.semantic());
            m.setTitleFitScore(result.titleFit());
            m.setAiRecommendations(finalRecs);
            m.setModelName(finalModel);
            result.gaps().forEach(m::addSkillGap);
            matchRepository.saveAndFlush(m);
            log.info("Match id={} {}% (resume v{}, job id={})", m.getId(), m.getMatchPercentage(),
                    resume.versionNumber(), job.id());
            return MatchResponse.from(matchRepository.findOwned(m.getId(), userId).orElseThrow());
        });
    }

    public MatchResponse get(Long userId, Long id) {
        return tx.execute(s -> MatchResponse.from(matchRepository.findOwned(id, userId)
                .orElseThrow(() -> ApiException.notFound("Match analysis"))));
    }

    public PagedResponse<MatchSummary> list(Long userId, Long jobId, Long resumeId, int page, int size) {
        return tx.execute(s -> PagedResponse.of(matchRepository.search(userId, jobId, resumeId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt"))), MatchSummary::from));
    }

    public void delete(Long userId, Long id) {
        tx.executeWithoutResult(s -> matchRepository.delete(matchRepository.findOwned(id, userId)
                .orElseThrow(() -> ApiException.notFound("Match analysis"))));
    }

    /** Skills detected in the user's latest completed GitHub analysis, lower-cased for lookup. */
    private Set<String> githubSkills(Long userId) {
        return tx.execute(s -> githubRepository.findFirstByUserIdAndStatusOrderByIdDesc(userId, ProcessingStatus.COMPLETED)
                .map(a -> a.getDetectedSkills() == null ? Set.<String>of()
                        : a.getDetectedSkills().stream().map(x -> x.toLowerCase(Locale.ROOT)).collect(Collectors.toSet()))
                .orElse(Set.of()));
    }

    private record Recommendations(List<String> recommendations) { }

    private List<String> aiRecommendations(Long userId, Snapshot resume, JobData job, Result result) {
        try {
            String gaps = result.gaps().stream()
                    .filter(g -> g.getStatus() != com.smartresume.planner.matching.domain.SkillMatchStatus.MATCHED)
                    .limit(15)
                    .map(g -> "- " + g.getSkillName() + " (" + g.getImportance() + ", " + g.getStatus() + ")")
                    .collect(Collectors.joining("\n"));
            String system = AiPrompts.system("""
                    You are a career coach. Given a resume, a job description and the skill gaps found by an \
                    automated matcher, answer with JSON: {"recommendations": [...]} holding 3 to 5 short, concrete, \
                    actionable tips (each under 40 words) to improve this resume for this job. \
                    Only suggest claiming skills the resume gives evidence for; otherwise suggest learning them.""");
            String user = "Match score: " + result.match() + "%\nSkill gaps:\n" + gaps + "\n\n"
                    + AiPrompts.jobDescription(job.text()) + "\n\n" + AiPrompts.resume(resume.text());
            List<String> recs = ai.structured(userId, "MATCH_TIPS", system, user, Recommendations.class)
                    .recommendations();
            return recs == null ? List.of() : recs.stream().filter(r -> r != null && !r.isBlank()).limit(6).toList();
        } catch (ApiException e) {
            log.info("AI match tips unavailable ({}), using rule-based tips", e.getCode());
            return List.of();
        }
    }
}
