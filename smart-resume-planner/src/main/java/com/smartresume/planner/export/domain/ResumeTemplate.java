package com.smartresume.planner.export.domain;

import com.smartresume.planner.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Reference data seeded by Flyway; templatePath resolves to templates/{templatePath}.html. */
@Entity
@Table(name = "resume_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResumeTemplate extends AuditableEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "template_path", nullable = false, length = 255)
    private String templatePath;

    @Column(name = "thumbnail_key", length = 500)
    private String thumbnailKey;

    @Column(name = "ats_safe", nullable = false)
    private boolean atsSafe = true;

    @Column(nullable = false)
    private boolean active = true;
}
