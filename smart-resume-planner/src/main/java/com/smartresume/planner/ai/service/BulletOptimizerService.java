package com.smartresume.planner.ai.service;

import com.smartresume.planner.ai.domain.BulletRewrite;
import com.smartresume.planner.ai.domain.RewriteStatus;
import com.smartresume.planner.ai.dto.OptimizerDtos.AnalyzeBulletsRequest;
import com.smartresume.planner.ai.dto.OptimizerDtos.AnalyzeBulletsResponse;
import com.smartresume.planner.ai.dto.OptimizerDtos.ApplyChoice;
import com.smartresume.planner.ai.dto.OptimizerDtos.ApplyRewritesRequest;
import com.smartresume.planner.ai.dto.OptimizerDtos.BulletRewriteResponse;
import com.smartresume.planner.ai.repository.BulletRewriteRepository;
import com.smartresume.planner.ats.dto.AtsResult;
import com.smartresume.planner.ats.service.AtsService;
import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.matching.domain.JobDescription;
import com.smartresume.planner.matching.domain.JobRequirements.Requirement;
import com.smartresume.planner.matching.repository.JobDescriptionRepository;
import com.smartresume.planner.resume.domain.Resume;
import com.smartresume.planner.resume.domain.ResumeDocument;
import com.smartresume.planner.resume.domain.ResumeDocument.Experience;
import com.smartresume.planner.resume.domain.ResumeDocument.Project;
import com.smartresume.planner.resume.domain.ResumeVersion;
import com.smartresume.planner.resume.domain.VersionSource;
import com.smartresume.planner.resume.dto.ResumeDtos.VersionDetail;
import com.smartresume.planner.resume.parse.Bullets;
import com.smartresume.planner.resume.parse.ResumeTextRenderer;
import com.smartresume.planner.resume.repository.ResumeRepository;
import com.smartresume.planner.resume.repository.ResumeVersionRepository;
import com.smartresume.planner.resume.service.ResumeLookup;
import com.smartresume.planner.resume.service.ResumeLookup.Snapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Finds weak bullet points and asks the LLM to rewrite them as impact statements
 * ("Accomplished X, measured by Y, by doing Z"). The model is told never to invent numbers: where the user's
 * bullet has no metric it must insert a bracketed placeholder, and those must be filled in before acceptance.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BulletOptimizerService {

    private static final int DEFAULT_MAX = 12;

    private final ResumeLookup resumeLookup;
    private final ResumeRepository resumeRepository;
    private final ResumeVersionRepository versionRepository;
    private final BulletRewriteRepository rewriteRepository;
    private final JobDescriptionRepository jobRepository;
    private final AtsService atsService;
    private final AiGateway ai;
    private final TransactionTemplate tx;

    record Candidate(int index, String section, String text, int score) { }

    record RewriteItem(int index, String rewritten, boolean needsMetric) { }

    record RewriteBatch(List<RewriteItem> rewrites) { }

    private record Draft(String section, String original, String rewritten, boolean needsMetric, int before,
                         int after) { }

    public AnalyzeBulletsResponse analyze(Long userId, AnalyzeBulletsRequest request) {
        Snapshot resume = resumeLookup.snapshot(userId, request.resumeId(), request.versionId());
        int max = request.maxBullets() == null ? DEFAULT_MAX : request.maxBullets();

        List<Candidate> all = collect(resume.document());
        List<Candidate> weak = all.stream().filter(c -> Bullets.isWeak(c.text()))
                .sorted(Comparator.comparingInt(Candidate::score)).limit(max).toList();
        if (weak.isEmpty()) {
            return new AnalyzeBulletsResponse(resume.versionId(), all.size(), 0, List.of(),
                    all.isEmpty() ? "No bullet points were found in this resume version."
                            : "Your bullets already read as strong. Nothing to rewrite.");
        }

        String jobContext = jobContext(userId, request.jobDescriptionId());
        RewriteBatch batch = ai.structured(userId, "BULLET_REWRITE", systemPrompt(), userPrompt(weak, jobContext),
                RewriteBatch.class);

        List<Draft> toSave = new ArrayList<>();
        Map<Integer, Candidate> byIndex = weak.stream().collect(Collectors.toMap(Candidate::index, c -> c));
        for (RewriteItem item : batch.rewrites() == null ? List.<RewriteItem>of() : batch.rewrites()) {
            Candidate c = byIndex.remove(item.index());
            if (c == null || item.rewritten() == null || item.rewritten().isBlank()
                    || item.rewritten().length() > 600) {
                continue;
            }
            String rewritten = item.rewritten().strip().replaceFirst("^[•\\-*]\\s*", "");
            boolean needsMetric = item.needsMetric() || Bullets.hasPlaceholder(rewritten);
            int after = Math.min(100, Bullets.impactScore(rewritten)
                    + (needsMetric && !Bullets.hasMetric(rewritten) ? 30 : 0));
            toSave.add(new Draft(c.section(), c.text(), rewritten, needsMetric, c.score(), after));
        }
        if (toSave.isEmpty()) {
            throw ApiException.unavailable("AI_BAD_OUTPUT",
                    "The AI did not return usable rewrites. Please try again.");
        }

        List<BulletRewriteResponse> saved = tx.execute(s -> {
            ResumeVersion version = versionRepository.getReferenceById(resume.versionId());
            rewriteRepository.deleteByVersionAndStatus(version.getId(), RewriteStatus.SUGGESTED);
            for (Draft d : toSave) {
                rewriteRepository.save(new BulletRewrite(version, d.section(), d.original(), d.rewritten(),
                        d.needsMetric(), d.before(), d.after(), ai.provider()));
            }
            rewriteRepository.flush();
            return rewriteRepository.findByResumeVersionIdOrderByIdAsc(version.getId()).stream()
                    .filter(r -> r.getStatus() == RewriteStatus.SUGGESTED)
                    .map(BulletRewriteResponse::from).toList();
        });
        return new AnalyzeBulletsResponse(resume.versionId(), all.size(), weak.size(), saved, null);
    }

    public List<BulletRewriteResponse> list(Long userId, Long resumeId, Long versionId) {
        Snapshot resume = resumeLookup.snapshot(userId, resumeId, versionId);
        return tx.execute(s -> rewriteRepository.findByResumeVersionIdOrderByIdAsc(resume.versionId()).stream()
                .map(BulletRewriteResponse::from).toList());
    }

    public BulletRewriteResponse reject(Long userId, Long rewriteId) {
        return tx.execute(s -> {
            BulletRewrite b = rewriteRepository.findOwned(rewriteId, userId)
                    .orElseThrow(() -> ApiException.notFound("Rewrite"));
            if (b.getStatus() == RewriteStatus.ACCEPTED) {
                throw ApiException.conflict("ALREADY_ACCEPTED", "This rewrite was already applied to a version");
            }
            b.reject();
            return BulletRewriteResponse.from(b);
        });
    }

    /** Applies the chosen rewrites to a copy of the source version and saves it as a new AI_REWRITE version. */
    public VersionDetail apply(Long userId, ApplyRewritesRequest request) {
        return tx.execute(s -> {
            Resume resume = resumeRepository.findByIdAndUserId(request.resumeId(), userId)
                    .orElseThrow(() -> ApiException.notFound("Resume"));
            List<Long> ids = request.choices().stream().map(ApplyChoice::rewriteId).distinct().toList();
            List<BulletRewrite> rewrites = rewriteRepository.findOwnedByIds(ids, userId);
            if (rewrites.size() != ids.size()) {
                throw ApiException.notFound("Rewrite");
            }
            Long sourceVersionId = rewrites.get(0).getResumeVersion().getId();
            for (BulletRewrite r : rewrites) {
                if (!r.getResumeVersion().getId().equals(sourceVersionId)) {
                    throw ApiException.badRequest("MIXED_VERSIONS", "All rewrites must come from the same resume version");
                }
                if (r.getStatus() == RewriteStatus.ACCEPTED) {
                    throw ApiException.conflict("ALREADY_ACCEPTED", "A selected rewrite was already applied");
                }
            }
            ResumeVersion source = versionRepository.findOwned(sourceVersionId, resume.getId(), userId)
                    .orElseThrow(() -> ApiException.badRequest("WRONG_RESUME", "Rewrites do not belong to this resume"));

            Map<Long, String> finalTexts = new HashMap<>();
            for (ApplyChoice choice : request.choices()) {
                BulletRewrite r = rewrites.stream().filter(x -> x.getId().equals(choice.rewriteId())).findFirst().orElseThrow();
                String text = choice.finalText() != null && !choice.finalText().isBlank()
                        ? choice.finalText().strip() : r.getRewrittenText();
                if (Bullets.hasPlaceholder(text)) {
                    throw ApiException.badRequest("PLACEHOLDER_NOT_FILLED",
                            "Replace the bracketed placeholder with your real number before accepting: \"" + text + "\"");
                }
                finalTexts.put(r.getId(), text);
            }

            ResumeDocument original = source.getStructuredContent();
            if (original == null) {
                throw ApiException.conflict("NO_STRUCTURE", "This version has no structured content to edit");
            }
            Map<String, String> replacements = new HashMap<>();
            rewrites.forEach(r -> replacements.put(r.getOriginalText().strip(), finalTexts.get(r.getId())));
            ResumeDocument edited = replaceBullets(original, replacements);

            String text = ResumeTextRenderer.toPlainText(edited);
            ResumeVersion version = ResumeVersion.derivedFrom(source, VersionSource.AI_REWRITE,
                    request.label() != null && !request.label().isBlank() ? request.label().trim()
                            : "AI rewrite (" + rewrites.size() + " bullet" + (rewrites.size() == 1 ? "" : "s") + ")",
                    text, edited);
            resume.addVersion(version);
            versionRepository.saveAndFlush(version);
            for (BulletRewrite r : rewrites) {
                r.accept(finalTexts.get(r.getId()), version);
            }
            AtsResult ats = AtsResult.from(atsService.analyzeAndSave(version));
            log.info("Applied {} rewrites as new version id={} of resume id={}", rewrites.size(), version.getId(),
                    resume.getId());
            return VersionDetail.of(version, resume.getId(), true, ats);
        });
    }

    // ------------------------------------------------------------------ helpers

    static List<Candidate> collect(ResumeDocument doc) {
        List<Candidate> out = new ArrayList<>();
        int index = 0;
        if (doc.experience() != null) {
            for (Experience e : doc.experience()) {
                String label = "EXPERIENCE: " + (e.title().isBlank() ? "Role" : e.title())
                        + (e.company().isBlank() ? "" : " at " + e.company());
                for (String b : e.bullets()) {
                    out.add(new Candidate(index++, label, b, Bullets.impactScore(b)));
                }
            }
        }
        if (doc.projects() != null) {
            for (Project p : doc.projects()) {
                for (String b : p.bullets()) {
                    out.add(new Candidate(index++, "PROJECT: " + p.name(), b, Bullets.impactScore(b)));
                }
            }
        }
        return out;
    }

    static ResumeDocument replaceBullets(ResumeDocument d, Map<String, String> replacements) {
        List<Experience> exp = d.experience() == null ? List.of() : d.experience().stream()
                .map(e -> new Experience(e.title(), e.company(), e.location(), e.startDate(), e.endDate(),
                        e.bullets().stream().map(b -> replacements.getOrDefault(b.strip(), b)).toList()))
                .toList();
        List<Project> projects = d.projects() == null ? List.of() : d.projects().stream()
                .map(p -> new Project(p.name(), p.url(), p.description(),
                        p.bullets().stream().map(b -> replacements.getOrDefault(b.strip(), b)).toList(),
                        p.technologies()))
                .toList();
        return new ResumeDocument(d.contact(), d.summary(), exp, d.education(), d.skills(), projects,
                d.certifications());
    }

    private String jobContext(Long userId, Long jobDescriptionId) {
        if (jobDescriptionId == null) {
            return null;
        }
        return tx.execute(s -> {
            JobDescription jd = jobRepository.findByIdAndUserId(jobDescriptionId, userId)
                    .orElseThrow(() -> ApiException.notFound("Job description"));
            String skills = jd.getExtractedSkills() == null ? "" : java.util.stream.Stream.of(
                            jd.getExtractedSkills().hardSkills(), jd.getExtractedSkills().tools())
                    .filter(java.util.Objects::nonNull).flatMap(List::stream).map(Requirement::name)
                    .limit(15).collect(Collectors.joining(", "));
            return "Target role: " + jd.getTitle() + (jd.getCompany() == null ? "" : " at " + jd.getCompany())
                    + (skills.isBlank() ? "" : "\nKey skills the role asks for: " + skills);
        });
    }

    private static String systemPrompt() {
        return AiPrompts.system("""
                You rewrite weak resume bullet points into high-impact achievement statements and answer with JSON only:
                {"rewrites":[{"index":<number>,"rewritten":"<text>","needsMetric":<true|false>}]}
                Rules:
                - Use the XYZ formula: "Accomplished [X] as measured by [Y], by doing [Z]".
                - Start with a strong past-tense action verb. No first person. 12 to 28 words. No trailing period needed.
                - Keep every technology, tool and fact from the original. Never invent employers, tools or results.
                - NEVER invent numbers. If the original has no metric, add a bracketed placeholder the user must fill in, \
                such as [X%], [N users] or [N hours/week], and set needsMetric to true. If it already has a real number, keep it \
                and set needsMetric to false.
                - Return one entry per input bullet, using the same index.""");
    }

    private static String userPrompt(List<Candidate> bullets, String jobContext) {
        StringBuilder sb = new StringBuilder();
        if (jobContext != null) {
            sb.append(jobContext).append("\nTailor wording toward this role where it is truthful.\n\n");
        }
        sb.append("<resume_bullets>\n");
        for (Candidate c : bullets) {
            sb.append(c.index()).append(" [").append(c.section()).append("]: ").append(c.text()).append('\n');
        }
        sb.append("</resume_bullets>");
        return sb.toString();
    }
}
