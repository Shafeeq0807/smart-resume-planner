package com.smartresume.planner.common.domain;

import com.smartresume.planner.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Tracks a 202-Accepted operation; the UUID is what the client polls at GET /jobs/{id}. */
@Entity
@Table(name = "async_jobs")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AsyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "job_type", nullable = false, length = 40)
    private AsyncJobType jobType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ProcessingStatus status = ProcessingStatus.PENDING;

    @Column(nullable = false)
    private int progress;

    @Column(name = "result_type", length = 50)
    private String resultType;

    @Column(name = "result_id")
    private Long resultId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public AsyncJob(User user, AsyncJobType jobType) {
        this.user = user;
        this.jobType = jobType;
    }

    public void start() {
        this.status = ProcessingStatus.RUNNING;
    }

    public void reportProgress(int percent) {
        this.progress = Math.max(0, Math.min(100, percent));
    }

    public void complete(String resultType, Long resultId) {
        this.status = ProcessingStatus.COMPLETED;
        this.progress = 100;
        this.resultType = resultType;
        this.resultId = resultId;
        this.completedAt = Instant.now();
    }

    public void fail(String message) {
        this.status = ProcessingStatus.FAILED;
        this.errorMessage = message == null ? null : message.substring(0, Math.min(message.length(), 1000));
        this.completedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AsyncJob other)) return false;
        return id != null && Objects.equals(id, other.getId());
    }

    @Override
    public int hashCode() {
        return AsyncJob.class.hashCode();
    }
}
