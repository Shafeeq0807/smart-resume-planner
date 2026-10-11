package com.smartresume.planner.common.repository;

import com.smartresume.planner.common.domain.AsyncJob;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsyncJobRepository extends JpaRepository<AsyncJob, UUID> {

    @Query("select j from AsyncJob j where j.id = :id and j.user.id = :userId")
    Optional<AsyncJob> findOwned(@Param("id") UUID id, @Param("userId") Long userId);

    /** Stuck-job sweeper: anything still PENDING/RUNNING after the cutoff was lost (e.g. server restart). */
    @Modifying
    @Query("update AsyncJob j set j.status = com.smartresume.planner.common.domain.ProcessingStatus.FAILED, "
            + "j.errorMessage = 'Interrupted (server restarted or timed out)', j.completedAt = :now "
            + "where j.status in (com.smartresume.planner.common.domain.ProcessingStatus.PENDING, "
            + "com.smartresume.planner.common.domain.ProcessingStatus.RUNNING) and j.updatedAt < :cutoff")
    int failStuck(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
