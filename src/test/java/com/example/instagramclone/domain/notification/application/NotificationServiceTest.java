package com.example.instagramclone.domain.notification.application;

import com.example.instagramclone.core.common.dto.SliceResponse;
import com.example.instagramclone.core.exception.NotificationErrorCode;
import com.example.instagramclone.core.exception.NotificationException;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.notification.domain.Notification;
import com.example.instagramclone.domain.notification.domain.NotificationRepository;
import com.example.instagramclone.domain.notification.domain.NotificationType;
import com.example.instagramclone.domain.notification.dto.NotificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * NotificationService 단위 테스트.
 *
 * [테스트 범위]
 * - getMyNotifications(): 필터·커서 기반 조회 결과를 SliceResponse로 변환
 * - markAsRead(): 알림 없음 예외, 본인 아닌 경우 예외, 정상 읽음 처리
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private Member buildMember(Long id, String username) {
        Member member = Member.builder()
                .username(username)
                .password("pw")
                .email(username + "@test.com")
                .name("이름")
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "profileImageUrl", "https://img/" + id + ".png");
        return member;
    }

    private Notification buildNotification(Long id, NotificationType type,
                                            Member receiver, Member sender, Long targetId) {
        Notification notification = Notification.create(type, receiver, sender, targetId,
                sender.getUsername() + "님 알림");
        ReflectionTestUtils.setField(notification, "id", id);
        ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.of(2026, 4, 13, 10, 0));
        return notification;
    }

    private NotificationResponse buildResponse(Long id, NotificationType type,
                                                 Member sender, Long targetId, String thumbnailUrl) {
        return new NotificationResponse(
                id, type.name(), sender.getId(), sender.getUsername(),
                sender.getProfileImageUrl(), targetId, thumbnailUrl,
                sender.getUsername() + "님 알림", false,
                LocalDateTime.of(2026, 4, 13, 10, 0));
    }

    @Nested
    @DisplayName("getMyNotifications()")
    class GetMyNotifications {

        @Test
        @DisplayName("성공 - 알림이 없으면 빈 SliceResponse 반환")
        void empty_result() {
            given(notificationRepository.findNotifications(1L, null, null, null, 20))
                    .willReturn(new SliceImpl<>(List.of(), Pageable.unpaged(), false));

            SliceResponse<NotificationResponse> response =
                    notificationService.getMyNotifications(1L, null, null, null, 20);

            assertThat(response.hasNext()).isFalse();
            assertThat(response.items()).isEmpty();
        }

        @Test
        @DisplayName("성공 - 알림 2건을 SliceResponse로 반환하고 hasNext 포함")
        void returns_notifications_with_has_next() {
            Member senderA = buildMember(2L, "koo");
            Member senderB = buildMember(3L, "mame");

            NotificationResponse r1 = buildResponse(10L, NotificationType.LIKE, senderA, 42L, "https://img/thumb.jpg");
            NotificationResponse r2 = buildResponse(9L, NotificationType.FOLLOW, senderB, null, null);

            given(notificationRepository.findNotifications(1L, null, null, null, 20))
                    .willReturn(new SliceImpl<>(List.of(r1, r2), Pageable.unpaged(), true));

            SliceResponse<NotificationResponse> response =
                    notificationService.getMyNotifications(1L, null, null, null, 20);

            assertThat(response.hasNext()).isTrue();
            assertThat(response.items()).hasSize(2);

            NotificationResponse first = response.items().get(0);
            assertThat(first.notificationId()).isEqualTo(10L);
            assertThat(first.type()).isEqualTo("LIKE");
            assertThat(first.senderId()).isEqualTo(2L);
            assertThat(first.senderUsername()).isEqualTo("koo");
            assertThat(first.targetId()).isEqualTo(42L);
            assertThat(first.targetThumbnailUrl()).isEqualTo("https://img/thumb.jpg");
            assertThat(first.isRead()).isFalse();

            NotificationResponse second = response.items().get(1);
            assertThat(second.type()).isEqualTo("FOLLOW");
            assertThat(second.targetId()).isNull();
            assertThat(second.targetThumbnailUrl()).isNull();
        }

        @Test
        @DisplayName("성공 - type 필터와 커서가 리포지토리에 그대로 전달된다")
        void passes_filter_parameters() {
            given(notificationRepository.findNotifications(1L, NotificationType.LIKE, false, 50L, 10))
                    .willReturn(new SliceImpl<>(List.of(), Pageable.unpaged(), false));

            notificationService.getMyNotifications(1L, NotificationType.LIKE, false, 50L, 10);

            then(notificationRepository).should()
                    .findNotifications(1L, NotificationType.LIKE, false, 50L, 10);
        }
    }

    @Nested
    @DisplayName("markAsRead()")
    class MarkAsRead {

        @Test
        @DisplayName("실패 - 알림이 존재하지 않으면 NOTIFICATION_NOT_FOUND 예외")
        void fail_not_found() {
            given(notificationRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markAsRead(999L, 1L))
                    .isInstanceOf(NotificationException.class)
                    .hasMessage(NotificationErrorCode.NOTIFICATION_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("실패 - 남의 알림을 읽음 처리하려 하면 NOTIFICATION_ACCESS_DENIED 예외")
        void fail_access_denied() {
            Member receiver = buildMember(1L, "owner");
            Member sender = buildMember(2L, "sender");
            Notification notification = buildNotification(10L, NotificationType.LIKE, receiver, sender, 42L);

            given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

            // loginMemberId = 99 (남의 알림)
            assertThatThrownBy(() -> notificationService.markAsRead(10L, 99L))
                    .isInstanceOf(NotificationException.class)
                    .hasMessage(NotificationErrorCode.NOTIFICATION_ACCESS_DENIED.getMessage());
        }

        @Test
        @DisplayName("성공 - 본인 알림 읽음 처리 → isRead가 true로 변경")
        void success_marks_as_read() {
            Member receiver = buildMember(1L, "me");
            Member sender = buildMember(2L, "sender");
            Notification notification = buildNotification(10L, NotificationType.COMMENT, receiver, sender, 42L);

            given(notificationRepository.findById(10L)).willReturn(Optional.of(notification));

            notificationService.markAsRead(10L, 1L);

            assertThat(notification.isRead()).isTrue();
        }
    }
}
