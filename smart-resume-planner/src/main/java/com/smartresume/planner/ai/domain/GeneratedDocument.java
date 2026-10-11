package com.smartresume.planner.ai.domain;

import com.smartresume.planner.common.domain.AuditableEntity;
import com.smartresume.planner.matching.domain.JobDescription;
import com.smartresume.planner.resume.domain.ResumeVersion;
import com.smartresume.planner.tracker.domain.JobApplication;
import com.smartresume.planner.user.domain.User;
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
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/** Cover letters and interview question sets. */
@Entity
@Table(name = "generated_documents")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeneratedDocument extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_version_id", nullable = false)
    private ResumeVersion resumeVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_description_id")
    private JobDescription jobDescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private JobApplication application;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "doc_type", nullable = false, length = 30)
    private GeneratedDocumentType docType;

    @Column(length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json", columnDefinition = "json")
    private Map<String, Object> contentJson;

    @Column(length = 30)
    private String tone;

    @Column(name = "model_name", length = 100)
    private String modelName;

    public GeneratedDocument(User user, ResumeVersion resumeVersion, GeneratedDocumentType docType,
                             String title, String content) {
        this.user = user;
        this.resumeVersion = resumeVersion;
        this.docType = docType;
        this.title = title;
        this.content = content;
    }
}
