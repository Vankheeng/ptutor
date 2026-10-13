package com.ptutor.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ptutor.backend.entity.enums.NotificationEventType;
import com.ptutor.backend.entity.enums.NotificationReferenceType;
import com.ptutor.backend.entity.enums.NotificationType;
import com.ptutor.backend.event.NotificationDomainEvent;

/** The wording of the "a new posting fits you" notification (design 07, 7.3). */
class RecommendationMatchNotificationTest {

    private final NotificationTemplateFactory factory = new NotificationTemplateFactory();

    @Test
    void namesTheTutorAndGivesOneReasonToAStudent() {
        NotificationContent content = factory.create(event(NotificationReferenceType.TEACHING_REQUEST,
                Map.of("name", "Nguyễn Văn Y", "title", "Dạy kèm Toán 9", "reason", "Học phí 180.000đ — nằm trong ngân sách")));

        assertThat(content.type()).isEqualTo(NotificationType.REQUEST);
        assertThat(content.title()).isEqualTo("Có gia sư mới rất phù hợp với tiêu chí của bạn");
        assertThat(content.content())
                .isEqualTo("Nguyễn Văn Y — Dạy kèm Toán 9. Học phí 180.000đ — nằm trong ngân sách.");
    }

    @Test
    void neverNamesTheStudentToATutor() {
        NotificationContent content = factory.create(event(NotificationReferenceType.STUDYING_REQUEST,
                Map.of("name", "Trần Thị B", "title", "Tìm gia sư Toán 9", "reason", "")));

        assertThat(content.title()).isEqualTo("Có học viên mới phù hợp với tiêu chí của bạn");
        assertThat(content.content()).isEqualTo("Tìm gia sư Toán 9. Xem tin học để biết thêm.");
        assertThat(content.content()).doesNotContain("Trần Thị B");
    }

    private NotificationDomainEvent event(NotificationReferenceType referenceType, Map<String, String> data) {
        return NotificationDomainEvent.deduplicated(UUID.randomUUID(), NotificationEventType.RECOMMENDATION_MATCH_FOUND,
                referenceType, UUID.randomUUID(), data, "rec-match:test");
    }
}
