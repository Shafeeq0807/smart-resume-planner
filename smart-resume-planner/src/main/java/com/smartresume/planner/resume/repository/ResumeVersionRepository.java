package com.smartresume.planner.resume.repository;

import com.smartresume.planner.resume.domain.ResumeVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResumeVersionRepository extends JpaRepository<ResumeVersion, Long> {

    /** A version, only if it belongs to the given resume AND that resume belongs to the user. */
    @Query("select v from ResumeVersion v where v.id = :versionId and v.resume.id = :resumeId "
            + "and v.resume.user.id = :userId")
    Optional<ResumeVersion> findOwned(@Param("versionId") Long versionId, @Param("resumeId") Long resumeId,
                                      @Param("userId") Long userId);

    /** The version only needs to belong to the user (used by features that take just a versionId). */
    @Query("select v from ResumeVersion v where v.id = :versionId and v.resume.user.id = :userId")
    Optional<ResumeVersion> findOwnedByUser(@Param("versionId") Long versionId, @Param("userId") Long userId);

    boolean existsByResumeIdAndChecksumSha256(Long resumeId, String checksum);

    /** List rows without the MEDIUMTEXT columns. */
    @Query("""
            select new com.smartresume.planner.resume.repository.VersionRow(
                v.id, v.versionNumber, v.label, v.source, v.originalFilename, v.fileSizeBytes,
                v.pageCount, v.wordCount, v.parentVersion.id, v.createdAt)
            from ResumeVersion v where v.resume.id = :resumeId order by v.versionNumber desc""")
    List<VersionRow> findRows(@Param("resumeId") Long resumeId);
}
