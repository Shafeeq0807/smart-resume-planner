package com.smartresume.planner.tracker.domain;

import com.smartresume.planner.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Append-only audit of card moves; powers the timeline view and funnel stats. */
@Entity
@Table(name = "application_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicationStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private JobApplication application;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "from_status", length = 20)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "to_status", nullable = false, length = 20)
    private ApplicationStatus toStatus;

    @Column(length = 500)
    private String note;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    ApplicationStatusHistory(JobApplication application, ApplicationStatus fromStatus,
                             ApplicationStatus toStatus, String note) {
        this.application = application;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.note = note;
        this.changedAt = Instant.now();
    }
}
