package com.smartresume.planner.ats.repository;

import com.smartresume.planner.ats.domain.AtsAnalysis;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AtsAnalysisRepository extends JpaRepository<AtsAnalysis, Long> {

    Optional<AtsAnalysis> findFirstByResumeVersionIdOrderByIdDesc(Long resumeVersionId);

    List<AtsAnalysis> findByResumeVersionIdOrderByIdDesc(Long resumeVersionId);

    /** Latest overall score per version, as [versionId, score] rows. */
    @Query("""
            select a.resumeVersion.id, a.overallScore from AtsAnalysis a
            where a.id in (select max(a2.id) from AtsAnalysis a2 where a2.resumeVersion.id in :versionIds
                           group by a2.resumeVersion.id)""")
    List<Object[]> latestScores(@Param("versionIds") Collection<Long> versionIds);
}
