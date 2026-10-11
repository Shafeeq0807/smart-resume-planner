package com.smartresume.planner.ats.service;

import com.smartresume.planner.ai.service.AiGateway;
import com.smartresume.planner.ai.service.AiPrompts;
import com.smartresume.planner.ats.domain.AtsAnalysis;
import com.smartresume.planner.ats.domain.AtsIssue;
import com.smartresume.planner.ats.dto.AtsResult;
import com.smartresume.planner.ats.repository.AtsAnalysisRepository;
import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.resume.domain.ResumeVersion;
import com.smartresume.planner.resume.parse.ResumeParser;
import com.smartresume.planner.resume.repository.ResumeVersionRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class AtsService {

    private final AtsAnalyzer analyzer;
    private final ResumeParser parser;
    private final AtsAnalysisRepository repository;
    private final ResumeVersionRepository versionRepository;
    private final AiGateway ai;
    private final TransactionTemplate tx;

    /** Called inside the upload/edit transaction: scores a freshly created version. */
    @Transactional
    public AtsAnalysis analyzeAndSave(ResumeVersion version) {
        AtsAnalysis analysis = analyzer.analyze(version, parser.parse(version.getExtractedText()));
        return repository.save(analysis);
    }

    @Transactional(readOnly = true)
    public AtsResult latest(Long userId, Long resumeId, Long versionId) {
        ResumeVersion version = owned(userId, resumeId, versionId);
        return repository.findFirstByResumeVersionIdOrderByIdDesc(version.getId())
                .map(AtsResult::from)
                .orElseThrow(() -> ApiException.notFound("ATS analysis"));
    }

    @Transactional(readOnly = true)
    public List<AtsResult> history(Long userId, Long resumeId, Long versionId) {
        ResumeVersion version = owned(userId, resumeId, versionId);
        return repository.findByResumeVersionIdOrderByIdDesc(version.getId()).stream().map(AtsResult::from).toList();
    }

    /** Re-runs the rules (e.g. after analyzer upgrades) and optionally adds a short AI coaching summary. */
    public AtsResult rerun(Long userId, Long resumeId, Long versionId, boolean withAiSummary) {
        AtsResult base = tx.execute(status -> {
            ResumeVersion version = owned(userId, resumeId, versionId);
            return AtsResult.from(analyzeAndSave(version));
        });
        if (!withAiSummary) {
            return base;
        }
        String resumeText = tx.execute(status -> owned(userId, resumeId, versionId).getExtractedText());
        String summary = aiSummary(userId, base, resumeText);
        return tx.execute(status -> {
            AtsAnalysis a = repository.findById(base.id()).orElseThrow();
            a.setAiSummary(summary);
            return AtsResult.from(a);
        });
    }

    private String aiSummary(Long userId, AtsResult result, String resumeText) {
        String issues = result.issues().stream()
                .filter(i -> i.severity() != AtsIssue.Severity.INFO)
                .limit(8)
                .map(i -> "- [" + i.severity() + "] " + i.message())
                .collect(Collectors.joining("\n"));
        String system = AiPrompts.system("""
                You are a resume coach. Write a 3-4 sentence plain-language summary of this resume's ATS \
                readiness: one sentence on the strongest point, then the two most important fixes. \
                Do not use markdown, bullet points or headings.""");
        String user = "ATS score: " + result.overallScore() + "/100\nTop issues:\n" + issues + "\n\n"
                + AiPrompts.resume(resumeText);
        return ai.text(userId, "ATS_SUMMARY", system, user);
    }

    private ResumeVersion owned(Long userId, Long resumeId, Long versionId) {
        return versionRepository.findOwned(versionId, resumeId, userId)
                .orElseThrow(() -> ApiException.notFound("Resume version"));
    }
}
