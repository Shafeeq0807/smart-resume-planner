package com.smartresume.planner.tracker.domain;

import com.smartresume.planner.common.domain.AuditableEntity;
import com.smartresume.planner.matching.domain.JobDescription;
import com.smartresume.planner.resume.domain.ResumeVersion;
import com.smartresume.planner.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A Kanban card. Ordering within a column uses a fractional boardOrder (midpoint on drop). */
@Entity
@Table(name = "job_applications")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobApplication extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_description_id")
    private JobDescription jobDescription;

    /** The exact resume version that was sent. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_version_id")
    private ResumeVersion resumeVersion;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "position_title", nullable = false, length = 200)
    private String positionTitle;

    @Column(name = "job_url", length = 1000)
    private String jobUrl;

    @Column(length = 150)
    private String location;

    @Column(name = "salary_range", length = 100)
    private String salaryRange;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.SAVED;

    @Setter(AccessLevel.NONE)
    @Column(name = "board_order", nullable = false)
    private double boardOrder;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "next_follow_up_at")
    private Instant nextFollowUpAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** Two tabs dragging the same card -> OptimisticLockException -> 409 Conflict. */
    @Version
    private Long version;

    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC")
    private List<ApplicationStatusHistory> statusHistory = new ArrayList<>();

    public JobApplication(User user, String companyName, String positionTitle, double boardOrder) {
        this.user = user;
        this.companyName = companyName;
        this.positionTitle = positionTitle;
        this.boardOrder = boardOrder;
        this.statusHistory.add(new ApplicationStatusHistory(this, null, ApplicationStatus.SAVED, "Created"));
    }

    /**
     * Drag-and-drop move. The service computes newOrder as the midpoint between the neighbouring
     * cards (or +/-1024 at the column edges) and re-spaces a column only when gaps get too small.
     */
    public void moveTo(ApplicationStatus newStatus, double newOrder, String note) {
        if (newStatus != this.status) {
            statusHistory.add(new ApplicationStatusHistory(this, this.status, newStatus, note));
            if (newStatus == ApplicationStatus.APPLIED && appliedAt == null) {
                appliedAt = Instant.now();
            }
            this.status = newStatus;
        }
        this.boardOrder = newOrder;
    }

    public List<ApplicationStatusHistory> getStatusHistory() {
        return Collections.unmodifiableList(statusHistory);
    }
}
