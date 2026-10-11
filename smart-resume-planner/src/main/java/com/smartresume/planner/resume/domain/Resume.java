package com.smartresume.planner.resume.domain;

import com.smartresume.planner.common.domain.AuditableEntity;
import com.smartresume.planner.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Aggregate root: owns its immutable versions. */
@Entity
@Table(name = "resumes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resume extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Setter
    @Column(nullable = false, length = 150)
    private String title;

    @Setter
    @Column(name = "target_role", length = 150)
    private String targetRole;

    /** Nullable while the first version is being created. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_version_id")
    private ResumeVersion currentVersion;

    @Setter
    @Column(nullable = false)
    private boolean archived;

    @Version
    private Long version;

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("versionNumber ASC")
    private List<ResumeVersion> versions = new ArrayList<>();

    public Resume(User user, String title, String targetRole) {
        this.user = user;
        this.title = title;
        this.targetRole = targetRole;
    }

    /**
     * Appends a new immutable version and makes it current. Concurrent appends are caught by
     * the unique (resume_id, version_number) constraint plus this entity's @Version.
     */
    public ResumeVersion addVersion(ResumeVersion newVersion) {
        int next = versions.stream().mapToInt(ResumeVersion::getVersionNumber).max().orElse(0) + 1;
        newVersion.attachTo(this, next);
        versions.add(newVersion);
        this.currentVersion = newVersion;
        return newVersion;
    }

    public void switchCurrentVersion(ResumeVersion target) {
        if (!versions.contains(target)) {
            throw new IllegalArgumentException("Version does not belong to this resume");
        }
        this.currentVersion = target;
    }

    public List<ResumeVersion> getVersions() {
        return Collections.unmodifiableList(versions);
    }
}
