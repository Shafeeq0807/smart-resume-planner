package com.smartresume.planner.github.repository;

import com.smartresume.planner.common.domain.ProcessingStatus;
import com.smartresume.planner.github.domain.GithubAnalysis;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubAnalysisRepository extends JpaRepository<GithubAnalysis, Long> {

    Optional<GithubAnalysis> findFirstByUserIdAndStatusOrderByIdDesc(Long userId, ProcessingStatus status);

    Optional<GithubAnalysis> findFirstByUserIdAndGithubUsernameIgnoreCaseAndStatusAndAnalyzedAtAfterOrderByIdDesc(
            Long userId, String githubUsername, ProcessingStatus status, Instant after);

    Page<GithubAnalysis> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "repositories")
    Optional<GithubAnalysis> findWithRepositoriesByIdAndUserId(Long id, Long userId);

    Optional<GithubAnalysis> findByIdAndUserId(Long id, Long userId);
}
