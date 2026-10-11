package com.smartresume.planner.ai.repository;

import com.smartresume.planner.ai.domain.AiUsageLog;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    @Query("select count(l) from AiUsageLog l where l.user.id = :userId and l.createdAt >= :since")
    long countSince(@Param("userId") Long userId, @Param("since") Instant since);
}
