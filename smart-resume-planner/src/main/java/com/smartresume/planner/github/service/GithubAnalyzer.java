package com.smartresume.planner.github.service;

import com.smartresume.planner.github.service.GithubClient.Repo;
import com.smartresume.planner.matching.domain.SkillType;
import com.smartresume.planner.matching.service.SkillCatalog;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Scoring heuristics for a GitHub repository (README quality, project complexity, recency, detected technologies). */
@Component
@RequiredArgsConstructor
public class GithubAnalyzer {

    private static final Pattern HEADING = Pattern.compile("(?m)^#{1,4}\\s+\\S");
    private static final Pattern INSTALL = Pattern.compile("(?i)(install|getting started|usage|quick ?start|how to run|setup|build)");
    private static final Pattern CODE_BLOCK = Pattern.compile("```");
    private static final Pattern IMAGE = Pattern.compile("(?i)(!\\[.*?\\]\\(.*?\\)|<img\\s|shields\\.io|badge)");
    private static final Pattern FEATURES = Pattern.compile("(?i)(features|tech stack|built with|technologies|architecture)");
    private static final Pattern LICENSE = Pattern.compile("(?i)licen[sc]e");

    private final SkillCatalog catalog;

    public record RepoScores(int readme, int complexity, int recency, List<String> tech) { }

    public RepoScores score(Repo repo, Map<String, Long> languages, String readme) {
        return new RepoScores(readmeQuality(readme), complexity(repo, languages), recency(repo.pushedAt()),
                detectTech(repo, languages, readme));
    }

    int readmeQuality(String readme) {
        if (readme == null || readme.isBlank()) {
            return 0;
        }
        int score = 0;
        if (readme.length() >= 500) {
            score += 25;
        } else if (readme.length() >= 150) {
            score += 12;
        }
        if (HEADING.matcher(readme).find()) {
            score += 15;
        }
        if (INSTALL.matcher(readme).find()) {
            score += 20;
        }
        if (CODE_BLOCK.matcher(readme).find()) {
            score += 15;
        }
        if (IMAGE.matcher(readme).find()) {
            score += 10;
        }
        if (FEATURES.matcher(readme).find()) {
            score += 10;
        }
        if (LICENSE.matcher(readme).find()) {
            score += 5;
        }
        return Math.min(100, score);
    }

    int complexity(Repo repo, Map<String, Long> languages) {
        double sizePart = Math.min(40, repo.size() / 50.0);                 // repo.size is KB
        double langPart = Math.min(25, languages.size() * 8.0);
        double starPart = Math.min(20, repo.stargazersCount() * 2.0);
        double forkPart = Math.min(15, repo.forksCount() * 3.0);
        return (int) Math.round(Math.min(100, sizePart + langPart + starPart + forkPart));
    }

    int recency(String pushedAt) {
        if (pushedAt == null) {
            return 20;
        }
        try {
            long days = Duration.between(Instant.parse(pushedAt), Instant.now()).toDays();
            return days <= 90 ? 100 : days <= 365 ? 60 : 20;
        } catch (DateTimeParseException e) {
            return 20;
        }
    }

    List<String> detectTech(Repo repo, Map<String, Long> languages, String readme) {
        Set<String> tech = new LinkedHashSet<>();
        languages.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> tech.add(catalog.normalize(e.getKey())));
        if (repo.topics() != null) {
            for (String topic : repo.topics()) {
                catalog.resolve(topic.replace('-', ' ')).ifPresent(s -> tech.add(s.name()));
            }
        }
        String text = (repo.description() == null ? "" : repo.description()) + "\n" + (readme == null ? "" : readme);
        catalog.findIn(text).stream().filter(s -> s.type() != SkillType.SOFT).forEach(s -> tech.add(s.name()));
        tech.removeIf(t -> t.isBlank() || List.of("Shell", "Makefile", "Dockerfile").contains(t));
        return new ArrayList<>(tech);
    }

    public int overall(List<RepoScores> repos, int distinctLanguages) {
        if (repos.isEmpty()) {
            return 0;
        }
        double avg = repos.stream().mapToDouble(r -> 0.4 * r.readme() + 0.4 * r.complexity() + 0.2 * r.recency())
                .average().orElse(0);
        double bonus = distinctLanguages >= 3 ? 5 : 0;
        return (int) Math.round(Math.min(100, avg + bonus));
    }

    /** Fallback bullet when AI is unavailable. Contains no invented numbers. */
    public String templateBullet(Repo repo, List<String> tech) {
        String what = repo.description() == null || repo.description().isBlank()
                ? "the " + repo.name() + " project" : repo.description().strip().replaceAll("[.\\s]+$", "");
        String stack = tech.isEmpty() ? "" : " using " + String.join(", ", tech.subList(0, Math.min(4, tech.size())));
        return "Built " + what + stack;
    }

    public Map<String, Long> languageTotals(Map<String, Map<String, Long>> perRepo) {
        Map<String, Long> totals = new LinkedHashMap<>();
        perRepo.values().forEach(m -> m.forEach((k, v) -> totals.merge(k, v, Long::sum)));
        return totals;
    }
}
