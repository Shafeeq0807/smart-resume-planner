-- =====================================================================
-- Smart Resume Planner :: V1 initial schema (MySQL 8.0+, InnoDB, utf8mb4)
-- Owned by Flyway. Hibernate runs with ddl-auto=validate against this.
--
-- Conventions
--   * Surrogate keys: BIGINT AUTO_INCREMENT (async_jobs uses CHAR(36) UUID).
--   * Timestamps: DATETIME(6), stored in UTC (hibernate.jdbc.time_zone=UTC).
--   * Enums: VARCHAR (mapped with @Enumerated(STRING)), so new values need no ALTER.
--   * Child rows: ON DELETE CASCADE. Optional links: ON DELETE SET NULL.
--   * Every user-owned table carries user_id and an index leading with it,
--     because every query is scoped by the authenticated user (IDOR guard).
-- =====================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    email            VARCHAR(255)  NOT NULL,
    password_hash    VARCHAR(100)  NOT NULL,
    full_name        VARCHAR(150)  NOT NULL,
    headline         VARCHAR(255)  NULL,
    github_username  VARCHAR(39)   NULL,
    role             VARCHAR(20)   NOT NULL DEFAULT 'USER',
    email_verified   BOOLEAN       NOT NULL DEFAULT FALSE,
    enabled          BOOLEAN       NOT NULL DEFAULT TRUE,
    last_login_at    DATETIME(6)   NULL,
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version          BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refresh_tokens (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    user_id           BIGINT        NOT NULL,
    token_hash        CHAR(64)      NOT NULL,               -- SHA-256 hex of the opaque token
    family_id         CHAR(36)      NOT NULL,               -- rotation family for reuse detection
    expires_at        DATETIME(6)   NOT NULL,
    revoked_at        DATETIME(6)   NULL,
    replaced_by_hash  CHAR(64)      NULL,
    user_agent        VARCHAR(255)  NULL,
    ip_address        VARCHAR(45)   NULL,                   -- fits IPv6
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user (user_id),
    KEY idx_refresh_tokens_family (family_id),
    KEY idx_refresh_tokens_expires (expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Resumes and immutable versions
-- ---------------------------------------------------------------------
CREATE TABLE resumes (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT        NOT NULL,
    title               VARCHAR(150)  NOT NULL,
    target_role         VARCHAR(150)  NULL,
    current_version_id  BIGINT        NULL,                 -- FK added after resume_versions exists
    archived            BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_resumes_user_list (user_id, archived, updated_at),
    KEY idx_resumes_current_version (current_version_id),
    CONSTRAINT fk_resumes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resume_versions (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    resume_id           BIGINT        NOT NULL,
    parent_version_id   BIGINT        NULL,
    version_number      INT           NOT NULL,
    label               VARCHAR(100)  NULL,
    source              VARCHAR(20)   NOT NULL,             -- UPLOAD | MANUAL_EDIT | AI_REWRITE | IMPORT
    original_filename   VARCHAR(255)  NULL,
    content_type        VARCHAR(100)  NULL,
    file_size_bytes     BIGINT        NULL,
    storage_key         VARCHAR(500)  NULL,                 -- key in FileStorage (disk / S3), never a user path
    checksum_sha256     CHAR(64)      NULL,
    extracted_text      MEDIUMTEXT    NULL,
    structured_content  JSON          NULL,                 -- ResumeDocument record
    page_count          INT           NULL,
    word_count          INT           NULL,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_resume_versions_number (resume_id, version_number),
    KEY idx_resume_versions_checksum (resume_id, checksum_sha256),
    KEY idx_resume_versions_parent (parent_version_id),
    CONSTRAINT fk_resume_versions_resume FOREIGN KEY (resume_id) REFERENCES resumes (id) ON DELETE CASCADE,
    CONSTRAINT fk_resume_versions_parent FOREIGN KEY (parent_version_id) REFERENCES resume_versions (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Circular reference resumes <-> resume_versions. Deleting a resume (or its user) still
-- cascades cleanly: InnoDB removes the versions and their analyses in one statement.
ALTER TABLE resumes
    ADD CONSTRAINT fk_resumes_current_version
        FOREIGN KEY (current_version_id) REFERENCES resume_versions (id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------
-- ATS evaluation
-- ---------------------------------------------------------------------
CREATE TABLE ats_analyses (
    id                    BIGINT        NOT NULL AUTO_INCREMENT,
    resume_version_id     BIGINT        NOT NULL,
    overall_score         INT           NOT NULL,           -- 0..100
    parseability_score    INT           NOT NULL,           -- 0..20
    structure_score       INT           NOT NULL,           -- 0..25
    formatting_score      INT           NOT NULL,           -- 0..20
    title_contact_score   INT           NOT NULL,           -- 0..15
    content_score         INT           NOT NULL,           -- 0..20
    detected_title        VARCHAR(150)  NULL,
    detected_sections     JSON          NULL,               -- ["CONTACT","EXPERIENCE",...]
    issues                JSON          NULL,               -- [AtsIssue]
    ai_summary            TEXT          NULL,
    analyzer_version      VARCHAR(20)   NOT NULL,
    created_at            DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ats_analyses_version_latest (resume_version_id, created_at),
    CONSTRAINT chk_ats_overall CHECK (overall_score BETWEEN 0 AND 100),
    CONSTRAINT fk_ats_analyses_version FOREIGN KEY (resume_version_id) REFERENCES resume_versions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Job matching and skill gaps
-- ---------------------------------------------------------------------
CREATE TABLE job_descriptions (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    user_id           BIGINT         NOT NULL,
    title             VARCHAR(200)   NOT NULL,
    company           VARCHAR(200)   NULL,
    location          VARCHAR(150)   NULL,
    source_url        VARCHAR(1000)  NULL,
    raw_text          MEDIUMTEXT     NOT NULL,
    text_hash         CHAR(64)       NOT NULL,              -- cache key for LLM skill extraction
    extracted_skills  JSON           NULL,                  -- JobRequirements record
    seniority         VARCHAR(30)    NULL,
    created_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_job_descriptions_user_created (user_id, created_at),
    KEY idx_job_descriptions_user_hash (user_id, text_hash),
    CONSTRAINT fk_job_descriptions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_match_analyses (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    resume_version_id     BIGINT         NOT NULL,
    job_description_id    BIGINT         NOT NULL,
    match_percentage      DECIMAL(5,2)   NOT NULL,
    hard_skill_coverage   DECIMAL(5,2)   NULL,
    soft_skill_coverage   DECIMAL(5,2)   NULL,
    semantic_score        DECIMAL(5,2)   NULL,
    title_fit_score       DECIMAL(5,2)   NULL,
    ai_recommendations    JSON           NULL,
    model_name            VARCHAR(100)   NULL,
    analyzer_version      VARCHAR(20)    NOT NULL,
    created_at            DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_job_match_version_jd (resume_version_id, job_description_id, created_at),
    KEY idx_job_match_jd (job_description_id),
    CONSTRAINT chk_job_match_pct CHECK (match_percentage BETWEEN 0 AND 100),
    CONSTRAINT fk_job_match_version FOREIGN KEY (resume_version_id) REFERENCES resume_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_match_jd FOREIGN KEY (job_description_id) REFERENCES job_descriptions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE skill_gaps (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    match_analysis_id  BIGINT        NOT NULL,
    skill_name         VARCHAR(100)  NOT NULL,              -- as written in the JD
    normalized_name    VARCHAR(100)  NOT NULL,              -- canonical form after alias resolution
    skill_type         VARCHAR(20)   NOT NULL,              -- HARD | SOFT | TOOL | CERTIFICATION
    importance         VARCHAR(20)   NOT NULL,              -- REQUIRED | PREFERRED
    status             VARCHAR(20)   NOT NULL,              -- MATCHED | PARTIAL | MISSING
    evidence           VARCHAR(500)  NULL,                  -- sentence that proved the skill
    evidence_source    VARCHAR(20)   NULL,                  -- RESUME | GITHUB
    PRIMARY KEY (id),
    KEY idx_skill_gaps_match (match_analysis_id),
    KEY idx_skill_gaps_status_name (status, normalized_name),   -- "most-missed skills" analytics
    CONSTRAINT fk_skill_gaps_match FOREIGN KEY (match_analysis_id) REFERENCES job_match_analyses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- AI optimizer output
-- ---------------------------------------------------------------------
CREATE TABLE bullet_rewrites (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    resume_version_id      BIGINT        NOT NULL,          -- version the bullet came from
    applied_in_version_id  BIGINT        NULL,              -- version created when accepted
    section                VARCHAR(50)   NULL,
    original_text          TEXT          NOT NULL,
    rewritten_text         TEXT          NOT NULL,
    needs_user_metric      BOOLEAN       NOT NULL DEFAULT FALSE,   -- contains a [X%] placeholder
    impact_score_before    INT           NULL,
    impact_score_after     INT           NULL,
    status                 VARCHAR(20)   NOT NULL DEFAULT 'SUGGESTED',  -- SUGGESTED | ACCEPTED | REJECTED
    model_name             VARCHAR(100)  NULL,
    created_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_bullet_rewrites_version_status (resume_version_id, status),
    KEY idx_bullet_rewrites_applied (applied_in_version_id),
    CONSTRAINT fk_bullet_rewrites_version FOREIGN KEY (resume_version_id) REFERENCES resume_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_bullet_rewrites_applied FOREIGN KEY (applied_in_version_id) REFERENCES resume_versions (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Application tracker (Kanban)
-- ---------------------------------------------------------------------
CREATE TABLE job_applications (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    user_id             BIGINT         NOT NULL,
    job_description_id  BIGINT         NULL,
    resume_version_id   BIGINT         NULL,                -- the version actually sent
    company_name        VARCHAR(200)   NOT NULL,
    position_title      VARCHAR(200)   NOT NULL,
    job_url             VARCHAR(1000)  NULL,
    location            VARCHAR(150)   NULL,
    salary_range        VARCHAR(100)   NULL,
    status              VARCHAR(20)    NOT NULL DEFAULT 'SAVED',  -- SAVED | APPLIED | INTERVIEWING | OFFERED | REJECTED
    board_order         DOUBLE         NOT NULL,            -- fractional index within the column
    applied_at          DATETIME(6)    NULL,
    next_follow_up_at   DATETIME(6)    NULL,
    notes               TEXT           NULL,
    created_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version             BIGINT         NOT NULL DEFAULT 0,  -- optimistic locking for drag-and-drop
    PRIMARY KEY (id),
    KEY idx_job_applications_board (user_id, status, board_order),
    KEY idx_job_applications_follow_up (user_id, next_follow_up_at),
    KEY idx_job_applications_jd (job_description_id),
    KEY idx_job_applications_version (resume_version_id),
    CONSTRAINT fk_job_applications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_job_applications_jd FOREIGN KEY (job_description_id) REFERENCES job_descriptions (id) ON DELETE SET NULL,
    CONSTRAINT fk_job_applications_version FOREIGN KEY (resume_version_id) REFERENCES resume_versions (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE application_status_history (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    application_id  BIGINT        NOT NULL,
    from_status     VARCHAR(20)   NULL,                     -- NULL for the creation event
    to_status       VARCHAR(20)   NOT NULL,
    note            VARCHAR(500)  NULL,
    changed_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_status_history_app (application_id, changed_at),
    CONSTRAINT fk_status_history_app FOREIGN KEY (application_id) REFERENCES job_applications (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Cover letters and interview question sets
CREATE TABLE generated_documents (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT        NOT NULL,
    resume_version_id   BIGINT        NOT NULL,
    job_description_id  BIGINT        NULL,
    application_id      BIGINT        NULL,
    doc_type            VARCHAR(30)   NOT NULL,             -- COVER_LETTER | INTERVIEW_QUESTIONS
    title               VARCHAR(200)  NULL,
    content             MEDIUMTEXT    NOT NULL,             -- rendered text / markdown
    content_json        JSON          NULL,                 -- structured output (e.g. questions + model answers)
    tone                VARCHAR(30)   NULL,
    model_name          VARCHAR(100)  NULL,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_generated_docs_user_type (user_id, doc_type, created_at),
    KEY idx_generated_docs_version (resume_version_id),
    KEY idx_generated_docs_jd (job_description_id),
    KEY idx_generated_docs_app (application_id),
    CONSTRAINT fk_generated_docs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_generated_docs_version FOREIGN KEY (resume_version_id) REFERENCES resume_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_generated_docs_jd FOREIGN KEY (job_description_id) REFERENCES job_descriptions (id) ON DELETE SET NULL,
    CONSTRAINT fk_generated_docs_app FOREIGN KEY (application_id) REFERENCES job_applications (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Feature 1: AI feedback chatbot
-- ---------------------------------------------------------------------
CREATE TABLE chat_sessions (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    user_id             BIGINT        NOT NULL,
    resume_version_id   BIGINT        NULL,                 -- grounding context
    job_description_id  BIGINT        NULL,                 -- optional grounding context
    title               VARCHAR(200)  NULL,
    last_message_at     DATETIME(6)   NULL,
    created_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_chat_sessions_user_recent (user_id, last_message_at),
    KEY idx_chat_sessions_version (resume_version_id),
    KEY idx_chat_sessions_jd (job_description_id),
    CONSTRAINT fk_chat_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_sessions_version FOREIGN KEY (resume_version_id) REFERENCES resume_versions (id) ON DELETE SET NULL,
    CONSTRAINT fk_chat_sessions_jd FOREIGN KEY (job_description_id) REFERENCES job_descriptions (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_messages (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    session_id         BIGINT        NOT NULL,
    role               VARCHAR(20)   NOT NULL,              -- USER | ASSISTANT | SYSTEM
    content            MEDIUMTEXT    NOT NULL,
    prompt_tokens      INT           NULL,
    completion_tokens  INT           NULL,
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_chat_messages_session_time (session_id, created_at, id),   -- memory window + history paging
    CONSTRAINT fk_chat_messages_session FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Feature 2: GitHub project analyzer
-- ---------------------------------------------------------------------
CREATE TABLE github_analyses (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    user_id          BIGINT         NOT NULL,
    github_username  VARCHAR(39)    NOT NULL,
    status           VARCHAR(20)    NOT NULL,               -- PENDING | RUNNING | COMPLETED | FAILED
    profile_summary  JSON           NULL,
    overall_score    INT            NULL,
    detected_skills  JSON           NULL,                   -- feeds the job matcher as evidence
    error_message    VARCHAR(1000)  NULL,
    analyzed_at      DATETIME(6)    NULL,
    created_at       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_github_analyses_user_created (user_id, created_at),
    KEY idx_github_analyses_username_fresh (github_username, analyzed_at),  -- 24h cache lookup
    CONSTRAINT fk_github_analyses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE github_repositories (
    id                    BIGINT         NOT NULL AUTO_INCREMENT,
    analysis_id           BIGINT         NOT NULL,
    repo_name             VARCHAR(200)   NOT NULL,
    repo_url              VARCHAR(500)   NOT NULL,
    description           VARCHAR(1000)  NULL,
    primary_language      VARCHAR(50)    NULL,
    languages             JSON           NULL,              -- {"Java": 120394, "TypeScript": 5530}
    stars                 INT            NOT NULL DEFAULT 0,
    forks                 INT            NOT NULL DEFAULT 0,
    last_pushed_at        DATETIME(6)    NULL,
    readme_quality_score  INT            NULL,
    complexity_score      INT            NULL,
    detected_tech         JSON           NULL,
    suggested_bullet      TEXT           NULL,
    included_in_resume    BOOLEAN        NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_github_repositories_name (analysis_id, repo_name),
    CONSTRAINT fk_github_repositories_analysis FOREIGN KEY (analysis_id) REFERENCES github_analyses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Feature 3: PDF exporter
-- ---------------------------------------------------------------------
CREATE TABLE resume_templates (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    code           VARCHAR(50)   NOT NULL,
    name           VARCHAR(100)  NOT NULL,
    description    VARCHAR(500)  NULL,
    template_path  VARCHAR(255)  NOT NULL,                  -- Thymeleaf template, e.g. resume/classic
    thumbnail_key  VARCHAR(500)  NULL,
    ats_safe       BOOLEAN       NOT NULL DEFAULT TRUE,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_resume_templates_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pdf_exports (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    user_id                BIGINT         NOT NULL,
    resume_version_id      BIGINT         NOT NULL,
    template_id            BIGINT         NOT NULL,
    status                 VARCHAR(20)    NOT NULL,         -- PENDING | RUNNING | COMPLETED | FAILED
    storage_key            VARCHAR(500)   NULL,
    file_size_bytes        BIGINT         NULL,
    page_count             INT            NULL,
    settings               JSON           NULL,             -- font, accent colour, margins, section order
    ats_self_check_score   INT            NULL,             -- parseability of the exported PDF
    error_message          VARCHAR(1000)  NULL,
    created_at             DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_pdf_exports_user_created (user_id, created_at),
    KEY idx_pdf_exports_version (resume_version_id),
    KEY idx_pdf_exports_template (template_id),
    CONSTRAINT fk_pdf_exports_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_pdf_exports_version FOREIGN KEY (resume_version_id) REFERENCES resume_versions (id) ON DELETE CASCADE,
    CONSTRAINT fk_pdf_exports_template FOREIGN KEY (template_id) REFERENCES resume_templates (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Cross-cutting: async job tracking and AI usage metering
-- ---------------------------------------------------------------------
CREATE TABLE async_jobs (
    id             CHAR(36)       NOT NULL,                 -- UUID returned with 202 Accepted
    user_id        BIGINT         NOT NULL,
    job_type       VARCHAR(40)    NOT NULL,                 -- ATS_ANALYSIS | MATCH_ANALYSIS | GITHUB_ANALYSIS | PDF_EXPORT
    status         VARCHAR(20)    NOT NULL,
    progress       INT            NOT NULL DEFAULT 0,       -- 0..100
    result_type    VARCHAR(50)    NULL,                     -- e.g. JobMatchAnalysis
    result_id      BIGINT         NULL,
    error_message  VARCHAR(1000)  NULL,
    created_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    completed_at   DATETIME(6)    NULL,
    PRIMARY KEY (id),
    KEY idx_async_jobs_user_created (user_id, created_at),
    KEY idx_async_jobs_status_updated (status, updated_at),  -- stuck-job sweeper
    CONSTRAINT fk_async_jobs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_usage_logs (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    feature            VARCHAR(40)   NOT NULL,              -- ATS_SUMMARY | MATCH | BULLET_REWRITE | COVER_LETTER | CHAT | GITHUB ...
    provider           VARCHAR(30)   NULL,                  -- openai | groq | ollama
    model              VARCHAR(100)  NULL,
    prompt_tokens      INT           NULL,
    completion_tokens  INT           NULL,
    latency_ms         INT           NULL,
    success            BOOLEAN       NOT NULL,
    created_at         DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ai_usage_user_feature_time (user_id, feature, created_at),  -- quota checks
    KEY idx_ai_usage_time (created_at),                                 -- retention purge
    CONSTRAINT fk_ai_usage_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------
-- Seed data
-- ---------------------------------------------------------------------
INSERT INTO resume_templates (code, name, description, template_path, ats_safe) VALUES
    ('classic',   'Classic',        'Single column, serif headings, maximum ATS compatibility', 'resume/classic',   TRUE),
    ('modern',    'Modern Minimal', 'Single column, sans-serif, accent colour on headings',     'resume/modern',    TRUE),
    ('technical', 'Technical',      'Skills matrix first, project-heavy layout for engineers',  'resume/technical', TRUE);
