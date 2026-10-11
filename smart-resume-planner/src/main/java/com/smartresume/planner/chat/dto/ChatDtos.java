package com.smartresume.planner.chat.dto;

import com.smartresume.planner.chat.domain.ChatMessage;
import com.smartresume.planner.chat.domain.ChatRole;
import com.smartresume.planner.chat.domain.ChatSession;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ChatDtos {

    private ChatDtos() {
    }

    public record CreateSessionRequest(Long resumeId, Long versionId, Long jobDescriptionId,
                                       @Size(max = 200) String title) { }

    public record RenameSessionRequest(@NotBlank @Size(max = 200) String title) { }

    public record SendMessageRequest(@NotBlank @Size(max = 4000) String content) { }

    public record SessionResponse(Long id, String title, Long resumeId, Long resumeVersionId, Long jobDescriptionId,
                                  Instant lastMessageAt, Instant createdAt) {
        public static SessionResponse from(ChatSession s) {
            return new SessionResponse(s.getId(), s.getTitle(),
                    s.getResumeVersion() == null ? null : s.getResumeVersion().getResume().getId(),
                    s.getResumeVersion() == null ? null : s.getResumeVersion().getId(),
                    s.getJobDescription() == null ? null : s.getJobDescription().getId(), s.getLastMessageAt(),
                    s.getCreatedAt());
        }
    }

    public record MessageResponse(Long id, ChatRole role, String content, Instant createdAt) {
        public static MessageResponse from(ChatMessage m) {
            return new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt());
        }
    }

    public record SessionDetail(SessionResponse session, List<MessageResponse> messages) { }

    public record ChatReply(Long sessionId, MessageResponse userMessage, MessageResponse assistantMessage) { }
}
