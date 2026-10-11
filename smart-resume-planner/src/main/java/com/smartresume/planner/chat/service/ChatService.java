package com.smartresume.planner.chat.service;

import com.smartresume.planner.ai.service.AiGateway;
import com.smartresume.planner.ai.service.AiPrompts;
import com.smartresume.planner.ats.repository.AtsAnalysisRepository;
import com.smartresume.planner.chat.domain.ChatMessage;
import com.smartresume.planner.chat.domain.ChatRole;
import com.smartresume.planner.chat.domain.ChatSession;
import com.smartresume.planner.chat.dto.ChatDtos.ChatReply;
import com.smartresume.planner.chat.dto.ChatDtos.CreateSessionRequest;
import com.smartresume.planner.chat.dto.ChatDtos.MessageResponse;
import com.smartresume.planner.chat.dto.ChatDtos.SessionDetail;
import com.smartresume.planner.chat.dto.ChatDtos.SessionResponse;
import com.smartresume.planner.chat.repository.ChatMessageRepository;
import com.smartresume.planner.chat.repository.ChatSessionRepository;
import com.smartresume.planner.common.exception.ApiException;
import com.smartresume.planner.common.util.Texts;
import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.matching.domain.JobDescription;
import com.smartresume.planner.matching.repository.JobDescriptionRepository;
import com.smartresume.planner.resume.repository.ResumeVersionRepository;
import com.smartresume.planner.resume.service.ResumeLookup;
import com.smartresume.planner.resume.service.ResumeLookup.Snapshot;
import com.smartresume.planner.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;

/** Feature 1: real-time AI resume feedback chatbot, grounded in a chosen resume version (and optionally a job). */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int MEMORY_WINDOW = 20;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ResumeLookup resumeLookup;
    private final ResumeVersionRepository versionRepository;
    private final JobDescriptionRepository jobRepository;
    private final AtsAnalysisRepository atsRepository;
    private final UserRepository userRepository;
    private final AiGateway ai;
    private final TransactionTemplate tx;

    /** Everything needed to call the model, captured while still inside the DB transaction. */
    private record Turn(Long sessionId, MessageResponse userMessage, List<Message> prompt, Long userId) { }

    // ------------------------------------------------------------------ sessions

    public SessionResponse createSession(Long userId, CreateSessionRequest request) {
        Snapshot resume = request.resumeId() == null ? null
                : resumeLookup.snapshot(userId, request.resumeId(), request.versionId());
        return tx.execute(s -> {
            JobDescription jd = request.jobDescriptionId() == null ? null
                    : jobRepository.findByIdAndUserId(request.jobDescriptionId(), userId)
                    .orElseThrow(() -> ApiException.notFound("Job description"));
            ChatSession session = new ChatSession(userRepository.getReferenceById(userId),
                    resume == null ? null : versionRepository.getReferenceById(resume.versionId()), jd,
                    Texts.blankToNull(request.title()));
            sessionRepository.saveAndFlush(session);
            return SessionResponse.from(session);
        });
    }

    public PagedResponse<SessionResponse> listSessions(Long userId, int page, int size) {
        return tx.execute(s -> PagedResponse.of(sessionRepository.findByUserId(userId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "updatedAt"))), SessionResponse::from));
    }

    public SessionDetail getSession(Long userId, Long sessionId) {
        return tx.execute(s -> {
            ChatSession session = load(userId, sessionId);
            List<ChatMessage> latest = new ArrayList<>(messageRepository.findTop100BySessionIdOrderByIdDesc(sessionId));
            Collections.reverse(latest);
            return new SessionDetail(SessionResponse.from(session), latest.stream().map(MessageResponse::from).toList());
        });
    }

    public SessionResponse rename(Long userId, Long sessionId, String title) {
        return tx.execute(s -> {
            ChatSession session = load(userId, sessionId);
            session.setTitle(title.trim());
            return SessionResponse.from(session);
        });
    }

    public void deleteSession(Long userId, Long sessionId) {
        tx.executeWithoutResult(s -> sessionRepository.delete(load(userId, sessionId)));
    }

    // ------------------------------------------------------------------ messaging

    /** Request/response chat: returns once the whole answer is ready. */
    public ChatReply send(Long userId, Long sessionId, String content) {
        Turn turn = prepare(userId, sessionId, content);
        String answer = ai.chat(userId, "CHAT", turn.prompt());
        return new ChatReply(sessionId, turn.userMessage(), saveAssistant(sessionId, answer));
    }

    /** Server-sent events: one {@code token} event per chunk, then a final {@code done} event with the saved message. */
    public Flux<ServerSentEvent<String>> stream(Long userId, Long sessionId, String content) {
        Turn turn = prepare(userId, sessionId, content);
        StringBuilder buffer = new StringBuilder();
        AtomicReference<MessageResponse> saved = new AtomicReference<>();
        Flux<ServerSentEvent<String>> tokens = ai.chatStream(userId, "CHAT", turn.prompt())
                .doOnNext(buffer::append)
                .map(chunk -> ServerSentEvent.builder(chunk).event("token").build());
        Flux<ServerSentEvent<String>> done = Flux.defer(() -> {
            String full = buffer.toString().trim();
            if (full.isEmpty()) {
                return Flux.just(ServerSentEvent.<String>builder("{\"error\":\"empty_answer\"}").event("error").build());
            }
            saved.set(saveAssistant(sessionId, full));
            return Flux.just(ServerSentEvent.<String>builder("{\"messageId\":" + saved.get().id() + "}")
                    .event("done").build());
        });
        return tokens.concatWith(done).onErrorResume(e -> {
            log.warn("Chat stream failed for session {}: {}", sessionId, e.toString());
            String message = e instanceof ApiException api ? api.getMessage() : "The assistant is unavailable right now.";
            return Flux.just(ServerSentEvent.<String>builder(
                    "{\"error\":\"" + message.replace("\"", "'") + "\"}").event("error").build());
        });
    }

    // ------------------------------------------------------------------ internals

    /** Stores the user's message and builds the full prompt (system context + memory window + new message). */
    private Turn prepare(Long userId, Long sessionId, String content) {
        String text = content.strip();
        // Load grounding context first (separate short transactions, no AI call yet).
        SessionContext ctx = tx.execute(s -> {
            ChatSession session = load(userId, sessionId);
            return new SessionContext(
                    session.getResumeVersion() == null ? null : session.getResumeVersion().getId(),
                    session.getJobDescription() == null ? null : session.getJobDescription().getRawText(),
                    session.getJobDescription() == null ? null : session.getJobDescription().getTitle(),
                    session.getTitle());
        });
        Snapshot resume = ctx.versionId() == null ? null : resumeLookup.snapshotByVersion(userId, ctx.versionId());

        return tx.execute(s -> {
            ChatSession session = load(userId, sessionId);
            List<ChatMessage> history = new ArrayList<>(messageRepository.findTop20BySessionIdOrderByIdDesc(sessionId));
            Collections.reverse(history);

            ChatMessage userMsg = session.addMessage(ChatRole.USER, text, null, null);
            if (session.getTitle() == null) {
                session.setTitle(Texts.truncate(text, 60));
            }
            sessionRepository.saveAndFlush(session);

            List<Message> prompt = new ArrayList<>();
            prompt.add(new SystemMessage(systemPrompt(resume, ctx)));
            for (ChatMessage m : history.subList(Math.max(0, history.size() - MEMORY_WINDOW), history.size())) {
                if (m.getRole() == ChatRole.USER) {
                    prompt.add(new UserMessage(m.getContent()));
                } else if (m.getRole() == ChatRole.ASSISTANT) {
                    prompt.add(new AssistantMessage(m.getContent()));
                }
            }
            prompt.add(new UserMessage(text));
            return new Turn(sessionId, MessageResponse.from(userMsg), prompt, userId);
        });
    }

    private record SessionContext(Long versionId, String jobText, String jobTitle, String title) { }

    private MessageResponse saveAssistant(Long sessionId, String content) {
        return tx.execute(s -> {
            ChatSession session = sessionRepository.findById(sessionId).orElseThrow();
            ChatMessage m = session.addMessage(ChatRole.ASSISTANT, content, null, null);
            sessionRepository.saveAndFlush(session);
            return MessageResponse.from(m);
        });
    }

    private String systemPrompt(Snapshot resume, SessionContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a friendly, expert resume coach chatting live with a job seeker.
                - Give specific, actionable feedback. When you suggest wording, show the exact rewritten line.
                - Keep answers short (under 180 words) unless the user asks for more detail.
                - Base advice on the resume and job description below. If something is missing, ask one clarifying question.
                - Never invent experience, numbers or skills for the user. If a metric is needed, ask them for it.
                - Stay on careers, resumes, applications and interviews; politely steer other topics back.
                """);
        if (resume != null) {
            sb.append("\nThe user is working on the resume \"").append(resume.resumeTitle()).append("\" (version ")
                    .append(resume.versionNumber()).append(").\n");
            atsRepository.findFirstByResumeVersionIdOrderByIdDesc(resume.versionId()).ifPresent(a -> {
                sb.append("Its automated ATS score is ").append(a.getOverallScore()).append("/100. Main issues: ");
                sb.append(a.getIssues() == null ? "none" : a.getIssues().stream().limit(5)
                        .map(i -> i.message()).reduce((x, y) -> x + "; " + y).orElse("none")).append(".\n");
            });
            sb.append(AiPrompts.resume(resume.text())).append('\n');
        } else {
            sb.append("\nNo resume is attached to this chat. Ask the user to paste relevant parts if needed.\n");
        }
        if (ctx.jobText() != null) {
            sb.append("\nTarget job: ").append(ctx.jobTitle()).append('\n').append(AiPrompts.jobDescription(ctx.jobText())).append('\n');
        }
        return AiPrompts.system(sb.toString());
    }

    private ChatSession load(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> ApiException.notFound("Chat session"));
    }
}
