package com.smartresume.planner.matching.repository;

import com.smartresume.planner.matching.domain.JobMatchAnalysis;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobMatchAnalysisRepository extends JpaRepository<JobMatchAnalysis, Long> {

    @EntityGraph(attributePaths = {"skillGaps", "jobDescription", "resumeVersion"})
    @Query("select m from JobMatchAnalysis m where m.id = :id and m.jobDescription.user.id = :userId")
    Optional<JobMatchAnalysis> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @EntityGraph(attributePaths = {"jobDescription", "resumeVersion"})
    @Query("select m from JobMatchAnalysis m where m.jobDescription.user.id = :userId "
            + "and (:jobId is null or m.jobDescription.id = :jobId) "
            + "and (:resumeId is null or m.resumeVersion.resume.id = :resumeId)")
    Page<JobMatchAnalysis> search(@Param("userId") Long userId, @Param("jobId") Long jobId,
                                  @Param("resumeId") Long resumeId, Pageable pageable);
}
