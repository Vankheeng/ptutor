package com.ptutor.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Keeps all contract expiry decisions in one configurable business timezone.
 */
@Component
public class ContractTimeProvider {

    private final Clock clock;
    private final ZoneId zoneId;

    public ContractTimeProvider(
            Clock clock,
            @Value("${app.contract.lifecycle.time-zone:Asia/Ho_Chi_Minh}") String timeZone) {
        this.clock = clock;
        this.zoneId = ZoneId.of(timeZone);
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(zoneId));
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(zoneId));
    }
}
