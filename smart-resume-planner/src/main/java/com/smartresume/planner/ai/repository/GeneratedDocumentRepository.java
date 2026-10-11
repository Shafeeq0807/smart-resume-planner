package com.smartresume.planner.ai.repository;

import com.smartresume.planner.ai.domain.GeneratedDocument;
import com.smartresume.planner.ai.domain.GeneratedDocumentType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, Long> {

    Optional<GeneratedDocument> findByIdAndUserId(Long id, Long userId);

    @Query("select d from GeneratedDocument d where d.user.id = :userId "
            + "and (:type is null or d.docType = :type) "
            + "and (:applicationId is null or d.application.id = :applicationId)")
    Page<GeneratedDocument> search(@Param("userId") Long userId, @Param("type") GeneratedDocumentType type,
                                   @Param("applicationId") Long applicationId, Pageable pageable);
}
