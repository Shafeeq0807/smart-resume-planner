package com.smartresume.planner.resume.repository;

import com.smartresume.planner.resume.domain.Resume;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Pattern for every user-owned repository: lookups are always scoped by userId,
 * so a guessed id from another account simply returns empty (404), never data.
 */
public interface ResumeRepository extends JpaRepository<Resume, Long> {

    @EntityGraph(attributePaths = "currentVersion")
    Page<Resume> findByUserIdAndArchivedFalse(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "currentVersion")
    Page<Resume> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "currentVersion")
    Optional<Resume> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"versions"})
    Optional<Resume> findWithVersionsByIdAndUserId(Long id, Long userId);
}
