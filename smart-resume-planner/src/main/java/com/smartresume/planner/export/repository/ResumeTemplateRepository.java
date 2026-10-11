package com.smartresume.planner.export.repository;

import com.smartresume.planner.export.domain.ResumeTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeTemplateRepository extends JpaRepository<ResumeTemplate, Long> {

    List<ResumeTemplate> findByActiveTrueOrderByIdAsc();

    Optional<ResumeTemplate> findByCodeAndActiveTrue(String code);
}
