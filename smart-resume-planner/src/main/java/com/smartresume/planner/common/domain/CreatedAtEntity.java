package com.smartresume.planner.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/** For append-only rows (analyses, versions, messages, logs). */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class CreatedAtEntity extends BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
