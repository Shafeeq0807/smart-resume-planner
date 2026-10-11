package com.smartresume.planner.matching.domain;

import com.smartresume.planner.common.domain.AuditableEntity;
import com.smartresume.planner.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "job_descriptions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobDescription extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 200)
    private String company;

    @Column(length = 150)
    private String location;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "raw_text", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String rawText;

    /** SHA-256 of normalised raw text; cache key for skill extraction. */
    @Column(name = "text_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String textHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_skills", columnDefinition = "json")
    private JobRequirements extractedSkills;

    @Column(length = 30)
    private String seniority;

    public JobDescription(User user, String title, String company, String rawText, String textHash) {
        this.user = user;
        this.title = title;
        this.company = company;
        this.rawText = rawText;
        this.textHash = textHash;
    }
}
