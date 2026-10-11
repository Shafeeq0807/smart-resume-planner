package com.smartresume.planner.chat.repository;

import com.smartresume.planner.chat.domain.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByIdAsc(Long sessionId);

    /** Newest first; callers reverse it to get the memory window in chronological order. */
    List<ChatMessage> findTop20BySessionIdOrderByIdDesc(Long sessionId);

    List<ChatMessage> findTop100BySessionIdOrderByIdDesc(Long sessionId);

    long countBySessionId(Long sessionId);
}
