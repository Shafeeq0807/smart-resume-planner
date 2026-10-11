package com.smartresume.planner.user.domain;

import com.smartresume.planner.common.domain.CreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.util.UUID;

/** Only the SHA-256 hash of the opaque token is stored. Rotation keeps the same familyId. */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "family_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_hash", columnDefinition = "CHAR(64)")
    private String replacedByHash;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    public RefreshToken(User user, String tokenHash, UUID familyId, Instant expiresAt,
                        String userAgent, String ipAddress) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    /** Marks this token as used; presenting it again means theft -> revoke the whole family. */
    public void rotateTo(String newTokenHash, Instant now) {
        this.revokedAt = now;
        this.replacedByHash = newTokenHash;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            this.revokedAt = now;
        }
    }
}
