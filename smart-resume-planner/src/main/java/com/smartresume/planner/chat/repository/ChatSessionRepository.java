package com.smartresume.planner.chat.repository;

import com.smartresume.planner.chat.domain.ChatSession;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);

    Page<ChatSession> findByUserId(Long userId, Pageable pageable);
}
