package com.smartresume.planner.github.service;

import com.smartresume.planner.ai.service.AiGateway;
import com.smartresume.planner.ai.service.AiPrompts;
import com.smartresume.planner.common.domain.AsyncJob;
import com.smartresume.planner.common.domain.ProcessingStatus;
import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.common.repository.AsyncJobRepository;
import com.smartresume.planner.common.util.Texts;
import com.smartresume.planner.github.domain.GithubAnalysis;
import com.smartresume.planner.github.domain.GithubRepository;
import com.smartresume.planner.github.repository.GithubAnalysisRepository;
import com.smartresume.planner.github.service.GithubAnalyzer.RepoScores;
import com.smartresume.planner.github.service.GithubClient.Repo;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs the slow part of a GitHub analysis (many HTTP calls + one AI call) off the request thread. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GithubAnalysisRunner {

    private final GithubClient client;
    private final GithubAnalyzer analyzer;
    private final GithubAnalysisRepository analysisRepository;
    private final AsyncJobRepository jobRepository;
    private final AiGateway ai;
    private final TransactionTemplate tx;

    record BulletItem(String repo, String bullet) { }

    record BulletBatch(List<BulletItem> bullets) { }

    private record Analyzed(Repo repo, Map<String, Long> languages, RepoScores scores) { }

    @Async
    public void run(UUID jobId, Long analysisId, Long userId, String username) {
        try {
            update(jobId, analysisId, ProcessingStatus.RUNNING, 5);
            List<Repo> repos = client.listRepos(username);
            update(jobId, analysisId, ProcessingStatus.RUNNING, 15);

            List<Analyzed> analyzed = new ArrayList<>();
            int done = 0;
            for (Repo repo : repos) {
                Map<String, Long> languages = client.languages(username, repo.name());
                String readme = client.readme(username, repo.name());
                analyzed.add(new Analyzed(repo, languages, analyzer.score(repo, languages, readme)));
                done++;
                update(jobId, analysisId, ProcessingStatus.RUNNING, 15 + (int) (65.0 * done / Math.max(1, repos.size())));
            }

            Map<String, String> bullets = bullets(userId, analyzed);
            update(jobId, analysisId, ProcessingStatus.RUNNING, 90);
            persist(jobId, analysisId, userId, analyzed, bullets);
            log.info("GitHub analysis id={} for '{}' completed ({} repos)", analysisId, username, analyzed.size());
        } catch (Exception e) {
            log.warn("GitHub analysis id={} failed: {}", analysisId, e.toString());
            String message = e instanceof ApiException api ? api.getMessage() : "The analysis failed unexpectedly";
            fail(jobId, analysisId, message);
        }
    }

    private void update(UUID jobId, Long analysisId, ProcessingStatus status, int progress) {
        tx.executeWithoutResult(s -> {
            AsyncJob job = jobRepository.findById(jobId).orElseThrow();
            job.start();
            job.reportProgress(progress);
            jobRepository.save(job);
            GithubAnalysis a = analysisRepository.findById(analysisId).orElseThrow();
            a.setStatus(status);
        });
    }

    private void fail(UUID jobId, Long analysisId, String message) {
        try {
            tx.executeWithoutResult(s -> {
                jobRepository.findById(jobId).ifPresent(j -> j.fail(message));
                analysisRepository.findById(analysisId).ifPresent(a -> {
                    a.setStatus(ProcessingStatus.FAILED);
                    a.setErrorMessage(Texts.truncate(message, 1000));
                });
            });
        } catch (RuntimeException e) {
            log.error("Could not record failure of GitHub analysis {}", analysisId, e);
        }
    }

    private void persist(UUID jobId, Long analysisId, Long userId, List<Analyzed> analyzed,
                         Map<String, String> bullets) {
        tx.executeWithoutResult(s -> {
            GithubAnalysis a = analysisRepository.findWithRepositoriesByIdAndUserId(analysisId, userId).orElseThrow();
            Map<String, Map<String, Long>> perRepoLanguages = new LinkedHashMap<>();
            Map<String, Integer> skillCounts = new LinkedHashMap<>();
            long totalStars = 0;
            Instant lastPushed = null;

            for (Analyzed x : analyzed) {
                Repo r = x.repo();
                GithubRepository entity = new GithubRepository(r.name(), r.htmlUrl());
                entity.setDescription(Texts.truncate(r.description(), 1000));
                entity.setPrimaryLanguage(Texts.truncate(r.language(), 50));
                entity.setLanguages(x.languages().isEmpty() ? null : new LinkedHashMap<>(x.languages()));
                entity.setStars(r.stargazersCount());
                entity.setForks(r.forksCount());
                Instant pushed = parse(r.pushedAt());
                entity.setLastPushedAt(pushed);
                entity.setReadmeQualityScore(x.scores().readme());
                entity.setComplexityScore(x.scores().complexity());
                entity.setDetectedTech(x.scores().tech());
                entity.setSuggestedBullet(bullets.getOrDefault(r.name(), analyzer.templateBullet(r, x.scores().tech())));
                a.addRepository(entity);

                perRepoLanguages.put(r.name(), x.languages());
                x.scores().tech().forEach(t -> skillCounts.merge(t, 1, Integer::sum));
                totalStars += r.stargazersCount();
                if (pushed != null && (lastPushed == null || pushed.isAfter(lastPushed))) {
                    lastPushed = pushed;
                }
            }

            Map<String, Long> totals = analyzer.languageTotals(perRepoLanguages);
            List<String> topLanguages = totals.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(8).map(Map.Entry::getKey).toList();
            Map<String, Object> summary = new HashMap<>();
            summary.put("repositoriesAnalyzed", analyzed.size());
            summary.put("totalStars", totalStars);
            summary.put("topLanguages", topLanguages);
            summary.put("lastActivity", lastPushed == null ? null : lastPushed.toString());
            summary.put("averageReadmeScore", (int) Math.round(analyzed.stream()
                    .mapToInt(x -> x.scores().readme()).average().orElse(0)));
            a.setProfileSummary(summary);
            a.setDetectedSkills(skillCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).map(Map.Entry::getKey)
                    .limit(60).collect(Collectors.toList()));
            a.setOverallScore(analyzer.overall(analyzed.stream().map(Analyzed::scores).toList(), totals.size()));
            a.setStatus(ProcessingStatus.COMPLETED);
            a.setAnalyzedAt(Instant.now());
            a.setErrorMessage(null);
            jobRepository.findById(jobId).orElseThrow().complete("GithubAnalysis", a.getId());
        });
    }

    /** One AI call for all repos; any failure falls back to template bullets (returned map is simply empty). */
    private Map<String, String> bullets(Long userId, List<Analyzed> analyzed) {
        if (analyzed.isEmpty()) {
            return Map.of();
        }
        try {
            String repos = analyzed.stream().limit(8).map(x -> "- " + x.repo().name() + ": "
                            + (x.repo().description() == null ? "(no description)" : x.repo().description())
                            + " | tech: " + String.join(", ", x.scores().tech().stream().limit(8).toList()))
                    .collect(Collectors.joining("\n"));
            String system = AiPrompts.system("""
                    You turn GitHub repositories into resume bullet points and answer with JSON only:
                    {"bullets":[{"repo":"<repo name>","bullet":"<text>"}]}
                    Rules: one bullet per repo, 12 to 25 words, start with a strong past-tense verb, mention the main technologies, \
                    describe what was built. Use ONLY the information given. Never invent numbers, users or results.""");
            BulletBatch batch = ai.structured(userId, "GITHUB_BULLETS", system,
                    "<repositories>\n" + repos + "\n</repositories>", BulletBatch.class);
            Map<String, String> out = new HashMap<>();
            if (batch.bullets() != null) {
                for (BulletItem item : batch.bullets()) {
                    if (item != null && item.repo() != null && item.bullet() != null && !item.bullet().isBlank()
                            && item.bullet().length() < 500) {
                        out.put(item.repo(), item.bullet().strip());
                    }
                }
            }
            return out;
        } catch (ApiException e) {
            log.info("AI bullets unavailable ({}), using templates", e.getCode());
            return Map.of();
        }
    }

    private static Instant parse(String iso) {
        try {
            return iso == null ? null : Instant.parse(iso);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
