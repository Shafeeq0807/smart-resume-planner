package com.smartresume.planner.chat.domain;

import com.smartresume.planner.common.domain.AuditableEntity;
import com.smartresume.planner.matching.domain.JobDescription;
import com.smartresume.planner.resume.domain.ResumeVersion;
import com.smartresume.planner.user.domain.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversation grounded in a resume version (and optionally a JD). Its id is the Spring AI
 * ChatMemory conversation id; JpaChatMemoryRepository reads the last N messages by query,
 * not through this collection.
 */
@Entity
@Table(name = "chat_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_version_id")
    private ResumeVersion resumeVersion;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_description_id")
    private JobDescription jobDescription;

    @Setter
    @Column(length = 200)
    private String title;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ChatMessage> messages = new ArrayList<>();

    public ChatSession(User user, ResumeVersion resumeVersion, JobDescription jobDescription, String title) {
        this.user = user;
        this.resumeVersion = resumeVersion;
        this.jobDescription = jobDescription;
        this.title = title;
    }

    public ChatMessage addMessage(ChatRole role, String content, Integer promptTokens, Integer completionTokens) {
        ChatMessage message = new ChatMessage(this, role, content, promptTokens, completionTokens);
        messages.add(message);
        this.lastMessageAt = Instant.now();
        return message;
    }
}
