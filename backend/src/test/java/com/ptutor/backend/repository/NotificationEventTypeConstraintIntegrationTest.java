package com.ptutor.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.ptutor.backend.entity.enums.NotificationEventType;

/**
 * Several migrations drop and recreate {@code ck_notifications_event_type} with a full list of values. A list
 * that forgets a value makes the database reject that notification, so the constraint left by the last
 * migration must accept every value of the enum.
 */
@SpringBootTest
class NotificationEventTypeConstraintIntegrationTest {

    private static final Pattern QUOTED_VALUE = Pattern.compile("'([A-Z_]+)'");

    @Autowired JdbcTemplate jdbc;

    @Test
    void theConstraintAcceptsEveryNotificationEventType() {
        String definition = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                        + "WHERE conname = 'ck_notifications_event_type' AND conrelid = 'notifications'::regclass",
                String.class);

        Set<String> accepted = new HashSet<>();
        Matcher matcher = QUOTED_VALUE.matcher(definition);
        while (matcher.find()) {
            accepted.add(matcher.group(1));
        }

        assertThat(accepted).containsAll(Arrays.stream(NotificationEventType.values()).map(Enum::name).toList());
    }
}
