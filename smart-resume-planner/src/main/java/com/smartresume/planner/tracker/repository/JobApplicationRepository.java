package com.smartresume.planner.tracker.repository;

import com.smartresume.planner.tracker.domain.ApplicationStatus;
import com.smartresume.planner.tracker.domain.JobApplication;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"statusHistory"})
    Optional<JobApplication> findWithHistoryByIdAndUserId(Long id, Long userId);

    List<JobApplication> findByUserIdOrderByStatusAscBoardOrderAsc(Long userId);

    List<JobApplication> findByUserIdAndStatusOrderByBoardOrderAsc(Long userId, ApplicationStatus status);

    @Query("select max(a.boardOrder) from JobApplication a where a.user.id = :userId and a.status = :status")
    Optional<Double> maxOrder(@Param("userId") Long userId, @Param("status") ApplicationStatus status);

    @Query("select a from JobApplication a where a.user.id = :userId and a.nextFollowUpAt is not null "
            + "and a.nextFollowUpAt <= :until and a.status in :activeStatuses order by a.nextFollowUpAt")
    List<JobApplication> findFollowUps(@Param("userId") Long userId, @Param("until") Instant until,
                                       @Param("activeStatuses") List<ApplicationStatus> activeStatuses);

    @Query("select a.status, count(a) from JobApplication a where a.user.id = :userId group by a.status")
    List<Object[]> countByStatus(@Param("userId") Long userId);
}
