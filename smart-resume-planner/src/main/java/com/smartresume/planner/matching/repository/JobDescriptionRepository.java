package com.smartresume.planner.matching.repository;

import com.smartresume.planner.matching.domain.JobDescription;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobDescriptionRepository extends JpaRepository<JobDescription, Long> {

    Page<JobDescription> findByUserId(Long userId, Pageable pageable);

    Optional<JobDescription> findByIdAndUserId(Long id, Long userId);

    Optional<JobDescription> findFirstByUserIdAndTextHashOrderByIdDesc(Long userId, String textHash);
}
