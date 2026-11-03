package com.ptutor.backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Giới hạn số tin gửi trợ lý của mỗi người dùng trong một cửa sổ thời gian trượt
 * (docs/support-chatbot/07-api.md mục 7.2: 20 tin / 5 phút; 09 mục 9.1 lớp 5).
 *
 * <p>Giữ trong bộ nhớ của tiến trình backend: đủ cho một bản backend. Chạy nhiều bản thì cần bộ đếm dùng chung
 * (Redis...), ghi ở hướng phát triển.
 */
@Component
public class AssistantRateLimiter {

    private final Clock clock;
    private final int maxMessages;
    private final Duration window;
    private final Map<UUID, Deque<Instant>> sentByUser = new ConcurrentHashMap<>();

    public AssistantRateLimiter(
            Clock clock,
            @Value("${app.assistant-service.rate-limit.messages:20}") int maxMessages,
            @Value("${app.assistant-service.rate-limit.window-seconds:300}") long windowSeconds) {
        this.clock = clock;
        this.maxMessages = maxMessages;
        this.window = Duration.ofSeconds(windowSeconds);
    }

    /** Ghi nhận một tin nếu còn hạn mức; trả {@code false} khi đã đủ số tin trong cửa sổ. */
    public boolean tryAcquire(UUID userId) {
        Instant now = clock.instant();
        Deque<Instant> sent = sentByUser.computeIfAbsent(userId, ignored -> new ArrayDeque<>());
        synchronized (sent) {
            while (!sent.isEmpty() && !sent.peekFirst().isAfter(now.minus(window))) {
                sent.pollFirst();
            }
            if (sent.size() >= maxMessages) {
                return false;
            }
            sent.addLast(now);
            return true;
        }
    }
}
