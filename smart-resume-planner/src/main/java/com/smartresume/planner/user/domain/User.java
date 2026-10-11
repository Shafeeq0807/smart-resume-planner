package com.smartresume.planner.user.domain;

import com.smartresume.planner.common.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Deliberately has NO @OneToMany collections: user-owned data is always loaded
 * through repository queries scoped by userId, never by walking user.getResumes().
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends AuditableEntity {

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(length = 255)
    private String headline;

    @Column(name = "github_username", length = 39)
    private String githubUsername;

    // @JdbcTypeCode(VARCHAR): Hibernate 6 would otherwise map STRING enums to a native MySQL ENUM
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Version
    private Long version;

    public User(String email, String passwordHash, String fullName) {
        this.email = email.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }
}
