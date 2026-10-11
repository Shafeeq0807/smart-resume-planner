package com.smartresume.planner.matching.service;

import com.smartresume.planner.ai.service.AiGateway;
import com.smartresume.planner.ai.service.AiPrompts;
import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.matching.domain.JobRequirements;
import com.smartresume.planner.matching.domain.JobRequirements.Requirement;
import com.smartresume.planner.matching.domain.SkillImportance;
import com.smartresume.planner.matching.domain.SkillType;
import com.smartresume.planner.matching.service.SkillCatalog.Skill;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Extracts structured skill requirements from a job description. The LLM does the heavy lifting; if it is
 * unavailable or returns too little, a catalog-based scan of the text is used so matching still works offline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRequirementsExtractor {

    private static final int MAX_PER_LIST = 30;
    private static final Pattern PREFERRED_MARK = Pattern.compile(
            "(?i)\\b(nice[- ]to[- ]have|preferred|bonus|good to have|a plus|desirable|nice to haves|optional)\\b");
    private static final Pattern REQUIRED_MARK = Pattern.compile(
            "(?i)^\\W*(requirements?|qualifications?|must[- ]have|required|responsibilities|what you.ll (do|need|bring)"
                    + "|about you|minimum qualifications|key skills)\\b.{0,40}$");
    private static final Pattern YEARS = Pattern.compile("(?i)(\\d{1,2})\\s*\\+?\\s*(?:-\\s*\\d+\\s*)?years");

    private final AiGateway ai;
    private final SkillCatalog catalog;

    public JobRequirements extract(Long userId, String title, String jobText) {
        JobRequirements fromCatalog = fromCatalog(jobText);
        try {
            JobRequirements fromAi = fromAi(userId, title, jobText);
            if (count(fromAi) >= 3) {
                return normalize(fromAi, fromCatalog.seniority());
            }
            log.info("AI returned too few requirements ({}); using catalog extraction", count(fromAi));
        } catch (ApiException e) {
            log.info("AI extraction unavailable ({}); using catalog extraction", e.getCode());
        } catch (RuntimeException e) {
            log.warn("AI extraction failed, using catalog extraction: {}", e.toString());
        }
        return fromCatalog;
    }

    private JobRequirements fromAi(Long userId, String title, String jobText) {
        String system = AiPrompts.system("""
                You extract skill requirements from a job description and answer with JSON only.
                Fields: hardSkills, softSkills, tools, certifications (arrays of {name, importance}) and seniority.
                - hardSkills: programming languages, frameworks, databases, protocols, methodologies (e.g. "Spring Boot", "REST APIs").
                - tools: concrete tools/platforms (e.g. "Docker", "Jira", "AWS").
                - softSkills: interpersonal skills (e.g. "Communication", "Leadership").
                - certifications: only named certifications.
                - importance is REQUIRED for must-haves and PREFERRED for nice-to-haves or "a plus".
                - Use short canonical names, 1-4 words, no sentences. At most %d items per list.
                - seniority is one of INTERN, JUNIOR, MID, SENIOR, LEAD, or null if unclear.
                Only include skills that the text actually mentions.""".formatted(MAX_PER_LIST));
        String user = "Job title: " + title + "\n\n" + AiPrompts.jobDescription(jobText);
        return ai.structured(userId, "JD_EXTRACT", system, user, JobRequirements.class);
    }

    private static int count(JobRequirements r) {
        return size(r.hardSkills()) + size(r.softSkills()) + size(r.tools()) + size(r.certifications());
    }

    private static int size(List<?> l) {
        return l == null ? 0 : l.size();
    }

    private JobRequirements normalize(JobRequirements r, String fallbackSeniority) {
        return new JobRequirements(clean(r.hardSkills()), clean(r.softSkills()), clean(r.tools()),
                clean(r.certifications()),
                r.seniority() == null || r.seniority().isBlank() || r.seniority().equalsIgnoreCase("null")
                        ? fallbackSeniority : r.seniority().toUpperCase(Locale.ROOT));
    }

    private List<Requirement> clean(List<Requirement> in) {
        Map<String, Requirement> out = new LinkedHashMap<>();
        if (in != null) {
            for (Requirement r : in) {
                if (r == null || r.name() == null || r.name().isBlank() || r.name().length() > 60) {
                    continue;
                }
                String name = catalog.normalize(r.name());
                out.putIfAbsent(name.toLowerCase(Locale.ROOT), new Requirement(name,
                        r.importance() == null ? SkillImportance.REQUIRED : r.importance()));
                if (out.size() >= MAX_PER_LIST) {
                    break;
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    /** Offline fallback: scans line by line, switching between REQUIRED and PREFERRED at section headings. */
    JobRequirements fromCatalog(String jobText) {
        Map<String, Requirement> hard = new LinkedHashMap<>();
        Map<String, Requirement> soft = new LinkedHashMap<>();
        Map<String, Requirement> tools = new LinkedHashMap<>();
        Map<String, Requirement> certs = new LinkedHashMap<>();
        SkillImportance mode = SkillImportance.REQUIRED;
        for (String line : jobText.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() < 70 && PREFERRED_MARK.matcher(trimmed).find()) {
                mode = SkillImportance.PREFERRED;
            } else if (REQUIRED_MARK.matcher(trimmed).matches()) {
                mode = SkillImportance.REQUIRED;
            }
            SkillImportance lineImportance = trimmed.length() >= 70 && PREFERRED_MARK.matcher(trimmed).find()
                    ? SkillImportance.PREFERRED : mode;
            for (Skill s : catalog.findIn(trimmed)) {
                Map<String, Requirement> target = switch (s.type()) {
                    case SOFT -> soft;
                    case TOOL -> tools;
                    case CERTIFICATION -> certs;
                    default -> hard;
                };
                String key = s.name().toLowerCase(Locale.ROOT);
                Requirement existing = target.get(key);
                // A skill mentioned as required anywhere stays required.
                if (existing == null || (existing.importance() == SkillImportance.PREFERRED
                        && lineImportance == SkillImportance.REQUIRED)) {
                    target.put(key, new Requirement(s.name(), lineImportance));
                }
            }
        }
        return new JobRequirements(limit(hard), limit(soft), limit(tools), limit(certs), detectSeniority(jobText));
    }

    private static List<Requirement> limit(Map<String, Requirement> m) {
        return m.values().stream().limit(MAX_PER_LIST).toList();
    }

    static String detectSeniority(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        if (t.matches("(?s).*\\b(intern|internship|trainee)\\b.*") && !t.contains("senior")) {
            return "INTERN";
        }
        if (t.matches("(?s).*\\b(principal|staff engineer|engineering manager|tech lead|team lead)\\b.*")) {
            return "LEAD";
        }
        if (t.matches("(?s).*\\b(senior|sr\\.)\\s.*")) {
            return "SENIOR";
        }
        if (t.matches("(?s).*\\b(junior|jr\\.|entry[- ]level|graduate|fresher)\\b.*")) {
            return "JUNIOR";
        }
        Matcher m = YEARS.matcher(text);
        int maxYears = -1;
        while (m.find()) {
            maxYears = Math.max(maxYears, Integer.parseInt(m.group(1)));
        }
        if (maxYears >= 7) {
            return "SENIOR";
        }
        if (maxYears >= 3) {
            return "MID";
        }
        if (maxYears >= 0) {
            return "JUNIOR";
        }
        return null;
    }
}
