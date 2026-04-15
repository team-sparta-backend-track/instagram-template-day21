package com.example.instagramclone.domain.notification.domain;

import com.example.instagramclone.domain.member.domain.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    private Member buildMember(Long id, String username) {
        Member member = Member.builder()
                .username(username)
                .password("pw")
                .email(username + "@test.com")
                .name("이름")
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    @DisplayName("create 팩토리로 모든 필드가 올바르게 설정된다")
    void create_sets_all_fields() {
        Member receiver = buildMember(1L, "receiver");
        Member sender = buildMember(2L, "sender");

        Notification notification = Notification.create(
                NotificationType.LIKE, receiver, sender, 42L, "좋아요 알림");

        assertThat(notification.getType()).isEqualTo(NotificationType.LIKE);
        assertThat(notification.getReceiver()).isSameAs(receiver);
        assertThat(notification.getSender()).isSameAs(sender);
        assertThat(notification.getTargetId()).isEqualTo(42L);
        assertThat(notification.getMessage()).isEqualTo("좋아요 알림");
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    @DisplayName("FOLLOW 알림은 targetId가 null이다")
    void follow_notification_has_null_target() {
        Member receiver = buildMember(1L, "receiver");
        Member sender = buildMember(2L, "sender");

        Notification notification = Notification.create(
                NotificationType.FOLLOW, receiver, sender, null, "팔로우 알림");

        assertThat(notification.getTargetId()).isNull();
    }

    @Test
    @DisplayName("markAsRead 호출 시 isRead가 true로 변경된다")
    void mark_as_read_sets_true() {
        Member receiver = buildMember(1L, "receiver");
        Member sender = buildMember(2L, "sender");

        Notification notification = Notification.create(
                NotificationType.COMMENT, receiver, sender, 10L, "댓글 알림");

        assertThat(notification.isRead()).isFalse();

        notification.markAsRead();

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    @DisplayName("이미 읽은 알림에 markAsRead를 다시 호출해도 true 유지")
    void mark_as_read_idempotent() {
        Member receiver = buildMember(1L, "receiver");
        Member sender = buildMember(2L, "sender");

        Notification notification = Notification.create(
                NotificationType.MENTION, receiver, sender, 5L, "멘션 알림");

        notification.markAsRead();
        notification.markAsRead();

        assertThat(notification.isRead()).isTrue();
    }
}
