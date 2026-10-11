package com.smartresume.planner.chat.web;

import com.smartresume.planner.chat.dto.ChatDtos.ChatReply;
import com.smartresume.planner.chat.dto.ChatDtos.CreateSessionRequest;
import com.smartresume.planner.chat.dto.ChatDtos.RenameSessionRequest;
import com.smartresume.planner.chat.dto.ChatDtos.SendMessageRequest;
import com.smartresume.planner.chat.dto.ChatDtos.SessionDetail;
import com.smartresume.planner.chat.dto.ChatDtos.SessionResponse;
import com.smartresume.planner.chat.service.ChatService;
import com.smartresume.planner.common.web.PagedResponse;
import com.smartresume.planner.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
@Tag(name = "AI feedback chatbot")
public class ChatController {

    private final ChatService service;

    @Operation(summary = "Start a chat grounded in a resume version and optionally a job description")
    @PostMapping
    public ResponseEntity<SessionResponse> create(@Valid @RequestBody CreateSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSession(CurrentUser.id(), request));
    }

    @GetMapping
    public PagedResponse<SessionResponse> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.listSessions(CurrentUser.id(), page, size);
    }

    @Operation(summary = "A session with its last 100 messages")
    @GetMapping("/{id}")
    public SessionDetail get(@PathVariable Long id) {
        return service.getSession(CurrentUser.id(), id);
    }

    @PatchMapping("/{id}")
    public SessionResponse rename(@PathVariable Long id, @Valid @RequestBody RenameSessionRequest request) {
        return service.rename(CurrentUser.id(), id, request.title());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteSession(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Send a message and get the complete answer")
    @PostMapping("/{id}/messages")
    public ChatReply send(@PathVariable Long id, @Valid @RequestBody SendMessageRequest request) {
        return service.send(CurrentUser.id(), id, request.content());
    }

    @Operation(summary = "Send a message and stream the answer as server-sent events (token / done / error)")
    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@PathVariable Long id, @Valid @RequestBody SendMessageRequest request) {
        return service.stream(CurrentUser.id(), id, request.content());
    }
}
