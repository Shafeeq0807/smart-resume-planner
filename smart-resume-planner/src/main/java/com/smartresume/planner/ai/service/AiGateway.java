package com.smartresume.planner.ai.service;

import com.smartresume.planner.common.config.AppProperties;
import com.smartresume.planner.common.exception.ApiException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

/**
 * The only class that talks to the LLM. It adds the things every call needs: a per-user hourly quota,
 * usage metering (including failures), and translation of provider errors into a clean 503 so callers can
 * fall back to rule-based results.
 */
@Slf4j
@Service
public class AiGateway {

    private final ChatClient chatClient;
    private final AiUsageRecorder recorder;
    private final AppProperties props;

    public AiGateway(ChatClient.Builder builder, AiUsageRecorder recorder, AppProperties props) {
        this.chatClient = builder.build();
        this.recorder = recorder;
        this.props = props;
    }

    /** Asks the model for JSON that Spring AI maps onto {@code type}. */
    public <T> T structured(Long userId, String feature, String system, String user, Class<T> type) {
        checkQuota(userId);
        long start = System.nanoTime();
        try {
            ResponseEntity<ChatResponse, T> result = chatClient.prompt()
                    .system(system)
                    .user(user)
                    .call()
                    .responseEntity(type);
            T entity = result.entity();
            if (entity == null) {
                throw new IllegalStateException("Model returned an empty or unparseable answer");
            }
            meter(userId, feature, result.response(), start, true);
            return entity;
        } catch (RuntimeException e) {
            throw failed(userId, feature, start, e);
        }
    }

    /** Free-text completion. */
    public String text(Long userId, String feature, String system, String user) {
        checkQuota(userId);
        long start = System.nanoTime();
        try {
            ChatResponse response = chatClient.prompt().system(system).user(user).call().chatResponse();
            String content = response == null || response.getResult() == null
                    ? null : response.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Model returned an empty answer");
            }
            meter(userId, feature, response, start, true);
            return content.trim();
        } catch (RuntimeException e) {
            throw failed(userId, feature, start, e);
        }
    }

    /** Multi-turn completion for the chatbot: full message list (system + history + new user message). */
    public String chat(Long userId, String feature, List<Message> messages) {
        checkQuota(userId);
        long start = System.nanoTime();
        try {
            ChatResponse response = chatClient.prompt().messages(messages).call().chatResponse();
            String content = response == null || response.getResult() == null
                    ? null : response.getResult().getOutput().getText();
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("Model returned an empty answer");
            }
            meter(userId, feature, response, start, true);
            return content.trim();
        } catch (RuntimeException e) {
            throw failed(userId, feature, start, e);
        }
    }

    /** Token stream for SSE chat. Quota is checked up front; usage is recorded when the stream ends. */
    public reactor.core.publisher.Flux<String> chatStream(Long userId, String feature, List<Message> messages) {
        checkQuota(userId);
        long start = System.nanoTime();
        return chatClient.prompt().messages(messages).stream().content()
                .doOnComplete(() -> safeRecord(userId, feature, start, true))
                .doOnError(e -> {
                    log.warn("AI stream failed [{}]: {}", feature, e.toString());
                    safeRecord(userId, feature, start, false);
                })
                .onErrorMap(e -> ApiException.unavailable("AI_UNAVAILABLE", unavailableMessage()));
    }

    public String provider() {
        return props.ai() == null || props.ai().provider() == null ? "unknown" : props.ai().provider();
    }

    // ------------------------------------------------------------------ internals

    private void checkQuota(Long userId) {
        long used = recorder.callsInLastHour(userId);
        if (used >= props.rateLimit().aiCallsPerHour()) {
            throw ApiException.tooManyRequests("AI request limit reached (" + props.rateLimit().aiCallsPerHour()
                    + " per hour). Please try again later.");
        }
    }

    private void meter(Long userId, String feature, ChatResponse response, long startNanos, boolean success) {
        Integer in = null;
        Integer out = null;
        String model = null;
        if (response != null && response.getMetadata() != null) {
            model = response.getMetadata().getModel();
            Usage usage = response.getMetadata().getUsage();
            if (usage != null) {
                in = usage.getPromptTokens();
                out = usage.getCompletionTokens();
            }
        }
        try {
            recorder.record(userId, feature, provider(), model, in, out, millisSince(startNanos), success);
        } catch (RuntimeException e) {
            log.warn("Could not record AI usage: {}", e.toString());
        }
    }

    private void safeRecord(Long userId, String feature, long startNanos, boolean success) {
        try {
            recorder.record(userId, feature, provider(), null, null, null, millisSince(startNanos), success);
        } catch (RuntimeException e) {
            log.warn("Could not record AI usage: {}", e.toString());
        }
    }

    private ApiException failed(Long userId, String feature, long startNanos, RuntimeException e) {
        if (e instanceof ApiException api) {
            return api;
        }
        log.warn("AI call failed [{}]: {}", feature, e.toString());
        safeRecord(userId, feature, startNanos, false);
        return ApiException.unavailable("AI_UNAVAILABLE", unavailableMessage());
    }

    private static String unavailableMessage() {
        return "The AI service is not available right now. Check the AI API key and model settings, then try again.";
    }

    private static int millisSince(long startNanos) {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000);
    }
}
