package com.smartresume.planner.matching.service;

import com.smartresume.planner.common.config.AppProperties;
import com.smartresume.planner.matching.domain.EvidenceSource;
import com.smartresume.planner.matching.domain.JobRequirements;
import com.smartresume.planner.matching.domain.JobRequirements.Requirement;
import com.smartresume.planner.matching.domain.SkillGap;
import com.smartresume.planner.matching.domain.SkillImportance;
import com.smartresume.planner.matching.domain.SkillMatchStatus;
import com.smartresume.planner.matching.domain.SkillType;
import com.smartresume.planner.matching.service.SkillCatalog.Skill;
import com.smartresume.planner.resume.domain.ResumeDocument;
import com.smartresume.planner.resume.domain.ResumeDocument.Experience;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Pure scoring logic for resume-vs-job matching. No I/O, so it is deterministic and unit-testable.
 * match% = w_hard * hardCoverage + w_soft * softCoverage + w_semantic * semantic + w_title * titleFit
 * (weights from {@code app.matching.weights}; a component with no data is dropped and the rest re-normalised).
 */
@Component
@RequiredArgsConstructor
public class MatchEngine {

    public static final String VERSION = "1.0";
    /** Cosine similarity at which the semantic score saturates at 100. Typical resume/JD pairs land at 0.1-0.4. */
    private static final double COSINE_SATURATION = 0.40;

    private final SkillCatalog catalog;
    private final AppProperties props;

    public record Input(String resumeText, ResumeDocument resume, String resumeTargetRole, Set<String> githubSkills,
                        String jobTitle, String jobText, JobRequirements requirements) { }

    public record Result(BigDecimal match, BigDecimal hardCoverage, BigDecimal softCoverage, BigDecimal semantic,
                         BigDecimal titleFit, List<SkillGap> gaps, List<String> recommendations) { }

    public Result compute(Input in) {
        List<SkillGap> gaps = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        addGaps(gaps, seen, in, in.requirements().hardSkills(), SkillType.HARD);
        addGaps(gaps, seen, in, in.requirements().tools(), SkillType.TOOL);
        addGaps(gaps, seen, in, in.requirements().certifications(), SkillType.CERTIFICATION);
        addGaps(gaps, seen, in, in.requirements().softSkills(), SkillType.SOFT);

        List<SkillGap> technical = gaps.stream().filter(g -> g.getSkillType() != SkillType.SOFT).toList();
        List<SkillGap> soft = gaps.stream().filter(g -> g.getSkillType() == SkillType.SOFT).toList();
        Double hard = coverage(technical);
        Double softCov = coverage(soft);

        double cosine = TextSimilarity.cosine(in.resumeText(), in.jobText());
        double semantic = Math.min(1.0, cosine / COSINE_SATURATION) * 100;
        double title = titleFit(in);

        var w = props.matching().weights();
        double total = 0;
        double weightSum = 0;
        if (hard != null) {
            total += w.hard() * hard;
            weightSum += w.hard();
        }
        if (softCov != null) {
            total += w.soft() * softCov;
            weightSum += w.soft();
        }
        total += w.semantic() * semantic;
        weightSum += w.semantic();
        total += w.title() * title;
        weightSum += w.title();
        double match = weightSum == 0 ? 0 : total / weightSum;

        return new Result(bd(match), bd(hard), bd(softCov), bd(semantic), bd(title), gaps,
                recommendations(gaps, hard, semantic));
    }

    // ------------------------------------------------------------------ skill gaps

    private void addGaps(List<SkillGap> gaps, Set<String> seen, Input in, List<Requirement> reqs, SkillType type) {
        if (reqs == null) {
            return;
        }
        for (Requirement r : reqs) {
            if (r == null || r.name() == null || r.name().isBlank()) {
                continue;
            }
            String normalized = catalog.normalize(r.name());
            if (!seen.add(normalized.toLowerCase(Locale.ROOT))) {
                continue;
            }
            SkillImportance importance = r.importance() == null ? SkillImportance.REQUIRED : r.importance();
            gaps.add(evaluate(in, r.name().trim(), normalized, type, importance));
        }
    }

    private SkillGap evaluate(Input in, String original, String normalized, SkillType type,
                              SkillImportance importance) {
        String skillName = truncate(original, 100);
        String normName = truncate(normalized, 100);
        Optional<Skill> known = catalog.resolve(normalized);

        if (known.isPresent()) {
            Skill skill = known.get();
            Optional<String> evidence = catalog.evidenceLine(in.resumeText(), skill);
            if (evidence.isPresent()) {
                return new SkillGap(skillName, normName, type, importance, SkillMatchStatus.MATCHED,
                        evidence.get(), EvidenceSource.RESUME);
            }
            if (in.githubSkills().contains(normalized.toLowerCase(Locale.ROOT))) {
                return new SkillGap(skillName, normName, type, importance, SkillMatchStatus.MATCHED,
                        "Found in your GitHub repositories", EvidenceSource.GITHUB);
            }
            if (type != SkillType.SOFT) {
                for (Skill related : catalog.relatedTo(skill)) {
                    if (related.appearsIn(in.resumeText())) {
                        return new SkillGap(skillName, normName, type, importance, SkillMatchStatus.PARTIAL,
                                "Related skill on your resume: " + related.name(), EvidenceSource.RESUME);
                    }
                }
            }
            return new SkillGap(skillName, normName, type, importance, SkillMatchStatus.MISSING, null, null);
        }

        // Not in the catalog: look for the literal phrase, then for all of its words.
        Pattern phrase = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(normalized)
                + "(?![\\p{L}\\p{N}])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        for (String line : in.resumeText().split("\n")) {
            if (phrase.matcher(line).find()) {
                return new SkillGap(skillName, normName, type, importance, SkillMatchStatus.MATCHED,
                        truncate(line.strip(), 300), EvidenceSource.RESUME);
            }
        }
        if (in.githubSkills().contains(normalized.toLowerCase(Locale.ROOT))) {
            return new SkillGap(skillName, normName, type, importance, SkillMatchStatus.MATCHED,
                    "Found in your GitHub repositories", EvidenceSource.GITHUB);
        }
        Set<String> words = TextSimilarity.tokenSet(normalized);
        if (words.size() >= 2 && TextSimilarity.tokenSet(in.resumeText()).containsAll(words)) {
            return new SkillGap(skillName, normName, type, importance, SkillMatchStatus.PARTIAL,
                    "All the words appear on your resume, but not as one phrase", EvidenceSource.RESUME);
        }
        return new SkillGap(skillName, normName, type, importance, SkillMatchStatus.MISSING, null, null);
    }

    /** Weighted coverage 0..100 (required counts double, partial counts half), or null when there is nothing to cover. */
    private static Double coverage(List<SkillGap> gaps) {
        double weight = 0;
        double earned = 0;
        for (SkillGap g : gaps) {
            double wt = g.getImportance() == SkillImportance.REQUIRED ? 2 : 1;
            double value = switch (g.getStatus()) {
                case MATCHED -> 1.0;
                case PARTIAL -> 0.5;
                case MISSING -> 0.0;
            };
            weight += wt;
            earned += wt * value;
        }
        return weight == 0 ? null : earned / weight * 100;
    }

    // ------------------------------------------------------------------ title fit

    private double titleFit(Input in) {
        Set<String> jobTokens = titleTokens(in.jobTitle());
        if (jobTokens.isEmpty()) {
            return 50;
        }
        List<String> candidates = new ArrayList<>();
        if (in.resume().contact() != null) {
            candidates.add(in.resume().contact().headline());
        }
        candidates.add(in.resumeTargetRole());
        if (in.resume().experience() != null) {
            candidates.addAll(in.resume().experience().stream().map(Experience::title).toList());
        }
        double best = 0;
        for (String c : candidates) {
            if (c == null || c.isBlank()) {
                continue;
            }
            Set<String> tokens = titleTokens(c);
            long overlap = jobTokens.stream().filter(tokens::contains).count();
            best = Math.max(best, (double) overlap / jobTokens.size());
        }
        return best * 100;
    }

    private static final Set<String> LEVEL_WORDS = Set.of("senior", "sr", "junior", "jr", "lead", "principal",
            "staff", "associate", "intern", "trainee", "i", "ii", "iii", "entry", "level", "mid");

    private static Set<String> titleTokens(String title) {
        if (title == null) {
            return Set.of();
        }
        return TextSimilarity.tokens(title.replace("-", " ").replace("/", " ")).stream()
                .filter(t -> !LEVEL_WORDS.contains(t))
                .map(MatchEngine::stem)
                .collect(Collectors.toSet());
    }

    private static String stem(String t) {
        return t.endsWith("s") && t.length() > 3 ? t.substring(0, t.length() - 1) : t;
    }

    // ------------------------------------------------------------------ recommendations

    private List<String> recommendations(List<SkillGap> gaps, Double hard, double semantic) {
        List<String> recs = new ArrayList<>();
        List<String> missingRequired = gaps.stream()
                .filter(g -> g.getStatus() == SkillMatchStatus.MISSING && g.getImportance() == SkillImportance.REQUIRED)
                .map(SkillGap::getSkillName).limit(5).toList();
        if (!missingRequired.isEmpty()) {
            recs.add("Required skills not found on your resume: " + String.join(", ", missingRequired)
                    + ". If you have used them, add a bullet that shows where and how. If not, consider "
                    + "a small project or course to close the gap.");
        }
        gaps.stream().filter(g -> g.getStatus() == SkillMatchStatus.PARTIAL).limit(3).forEach(g ->
                recs.add("\"" + g.getSkillName() + "\" is only partly covered (" + g.getEvidence()
                        + "). Name it explicitly if you have the experience."));
        List<String> missingPreferred = gaps.stream()
                .filter(g -> g.getStatus() == SkillMatchStatus.MISSING && g.getImportance() == SkillImportance.PREFERRED)
                .map(SkillGap::getSkillName).limit(4).toList();
        if (!missingPreferred.isEmpty()) {
            recs.add("Nice-to-have skills missing: " + String.join(", ", missingPreferred)
                    + ". Adding any you know improves your ranking.");
        }
        if (semantic < 40) {
            recs.add("Your wording differs a lot from the job description. Mirror its key terms where they are "
                    + "truthful, especially in your summary and skills section.");
        }
        if (hard != null && hard >= 80) {
            recs.add("Strong technical coverage. Make your top 3 bullets prove the job's main responsibilities with numbers.");
        }
        if (recs.isEmpty()) {
            recs.add("No major gaps found. Tailor your summary to the role's title and top responsibilities.");
        }
        return recs;
    }

    // ------------------------------------------------------------------ helpers

    private static BigDecimal bd(Double v) {
        return v == null ? null : bd(v.doubleValue());
    }

    private static BigDecimal bd(double v) {
        double clamped = Math.max(0, Math.min(100, v));
        return BigDecimal.valueOf(clamped).setScale(2, RoundingMode.HALF_UP);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
