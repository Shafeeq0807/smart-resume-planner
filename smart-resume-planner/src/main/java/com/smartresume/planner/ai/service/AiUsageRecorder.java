package com.smartresume.planner.ai.service;

import com.smartresume.planner.ai.domain.AiUsageLog;
import com.smartresume.planner.ai.repository.AiUsageLogRepository;
import com.smartresume.planner.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes usage rows in their own transaction so a failed AI call is still metered. */
@Component
@RequiredArgsConstructor
public class AiUsageRecorder {

    private final AiUsageLogRepository repository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String feature, String provider, String model, Integer promptTokens,
                       Integer completionTokens, int latencyMs, boolean success) {
        repository.save(new AiUsageLog(userRepository.getReferenceById(userId), feature, provider, model,
                promptTokens, completionTokens, latencyMs, success));
    }

    @Transactional(readOnly = true)
    public long callsInLastHour(Long userId) {
        return repository.countSince(userId, java.time.Instant.now().minusSeconds(3600));
    }
}
