package com.smartresume.planner.ai.domain;

import com.smartresume.planner.common.domain.CreatedAtEntity;
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

/** One row per LLM call; backs per-user quotas and cost dashboards. */
@Entity
@Table(name = "ai_usage_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUsageLog extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 40)
    private String feature;

    @Column(length = 30)
    private String provider;

    @Column(length = 100)
    private String model;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(nullable = false)
    private boolean success;

    public AiUsageLog(User user, String feature, String provider, String model, Integer promptTokens,
                      Integer completionTokens, Integer latencyMs, boolean success) {
        this.user = user;
        this.feature = feature;
        this.provider = provider;
        this.model = model;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.latencyMs = latencyMs;
        this.success = success;
    }
}
