package com.smartresume.planner.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.Instant;

/** For mutable rows. */
@MappedSuperclass
@Getter
public abstract class AuditableEntity extends CreatedAtEntity {

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
