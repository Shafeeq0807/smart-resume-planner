package com.smartresume.planner.export.repository;

import com.smartresume.planner.export.domain.PdfExport;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PdfExportRepository extends JpaRepository<PdfExport, Long> {

    @EntityGraph(attributePaths = {"template", "resumeVersion"})
    Optional<PdfExport> findByIdAndUserId(Long id, Long userId);

    @EntityGraph(attributePaths = {"template", "resumeVersion"})
    Page<PdfExport> findByUserId(Long userId, Pageable pageable);
}
