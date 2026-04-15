package com.example.instagramclone.domain.notification.event;

import com.example.instagramclone.domain.notification.domain.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventTest {

    @Test
    @DisplayName("sender와 receiver가 같으면 isSelfNotification이 true")
    void self_notification_returns_true() {
        NotificationEvent event = new NotificationEvent(
                NotificationType.LIKE, 1L, 1L, 10L, null);

        assertThat(event.isSelfNotification()).isTrue();
    }

    @Test
    @DisplayName("sender와 receiver가 다르면 isSelfNotification이 false")
    void different_users_returns_false() {
        NotificationEvent event = new NotificationEvent(
                NotificationType.LIKE, 1L, 2L, 10L, null);

        assertThat(event.isSelfNotification()).isFalse();
    }

    @Test
    @DisplayName("record 필드가 올바르게 저장된다")
    void fields_are_stored_correctly() {
        NotificationEvent event = new NotificationEvent(
                NotificationType.COMMENT, 3L, 5L, 42L, "테스트 메시지");

        assertThat(event.type()).isEqualTo(NotificationType.COMMENT);
        assertThat(event.receiverId()).isEqualTo(3L);
        assertThat(event.senderId()).isEqualTo(5L);
        assertThat(event.targetId()).isEqualTo(42L);
        assertThat(event.message()).isEqualTo("테스트 메시지");
    }

    @Test
    @DisplayName("FOLLOW 이벤트는 targetId가 null일 수 있다")
    void follow_event_target_can_be_null() {
        NotificationEvent event = new NotificationEvent(
                NotificationType.FOLLOW, 1L, 2L, null, null);

        assertThat(event.targetId()).isNull();
        assertThat(event.isSelfNotification()).isFalse();
    }
}
