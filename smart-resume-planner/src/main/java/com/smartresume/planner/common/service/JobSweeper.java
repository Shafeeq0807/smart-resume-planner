package com.smartresume.planner.common.service;

import com.smartresume.planner.common.repository.AsyncJobRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Marks background jobs that never finished (server restart, crash) as FAILED so clients stop polling. */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobSweeper {

    private final AsyncJobRepository repository;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    @Transactional
    public void sweep() {
        Instant now = Instant.now();
        int failed = repository.failStuck(now.minusSeconds(900), now);
        if (failed > 0) {
            log.warn("Marked {} stuck background job(s) as FAILED", failed);
        }
    }
}
