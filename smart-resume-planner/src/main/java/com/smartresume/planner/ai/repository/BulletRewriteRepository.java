package com.smartresume.planner.ai.repository;

import com.smartresume.planner.ai.domain.BulletRewrite;
import com.smartresume.planner.ai.domain.RewriteStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BulletRewriteRepository extends JpaRepository<BulletRewrite, Long> {

    @Query("select b from BulletRewrite b where b.id = :id and b.resumeVersion.resume.user.id = :userId")
    Optional<BulletRewrite> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select b from BulletRewrite b where b.id in :ids and b.resumeVersion.resume.user.id = :userId")
    List<BulletRewrite> findOwnedByIds(@Param("ids") List<Long> ids, @Param("userId") Long userId);

    List<BulletRewrite> findByResumeVersionIdOrderByIdAsc(Long resumeVersionId);

    @Modifying
    @Query("delete from BulletRewrite b where b.resumeVersion.id = :versionId and b.status = :status")
    int deleteByVersionAndStatus(@Param("versionId") Long versionId, @Param("status") RewriteStatus status);
}
