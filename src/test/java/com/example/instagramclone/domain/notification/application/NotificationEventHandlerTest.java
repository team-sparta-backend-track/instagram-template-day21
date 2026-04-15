package com.example.instagramclone.domain.notification.application;

import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import com.example.instagramclone.domain.notification.domain.Notification;
import com.example.instagramclone.domain.notification.domain.NotificationRepository;
import com.example.instagramclone.domain.notification.domain.NotificationType;
import com.example.instagramclone.domain.notification.event.NotificationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * NotificationEventHandler 단위 테스트.
 *
 * [테스트 범위]
 * - 자기 자신에 대한 알림은 무시
 * - 정상 이벤트: 4종 알림 각각 올바른 메시지로 저장
 * - 예외 발생 시 로그만 남기고 전파하지 않음
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventHandlerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private NotificationEventHandler handler;

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

    @Nested
    @DisplayName("handleNotification()")
    class HandleNotification {

        @Test
        @DisplayName("자기 자신에 대한 알림이면 저장하지 않는다")
        void skip_self_notification() {
            NotificationEvent event = new NotificationEvent(
                    NotificationType.LIKE, 1L, 1L, 10L, null);

            handler.handleNotification(event);

            then(notificationRepository).should(never()).save(any());
            then(memberRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("동일한 읽지 않은 알림이 이미 존재하면 저장하지 않는다 (중복 방지)")
        void skip_duplicate_unread_notification() {
            given(notificationRepository
                    .existsByTypeAndReceiverIdAndSenderIdAndTargetIdAndIsReadFalse(
                            NotificationType.LIKE, 1L, 2L, 42L))
                    .willReturn(true);

            NotificationEvent event = new NotificationEvent(
                    NotificationType.LIKE, 1L, 2L, 42L, null);

            handler.handleNotification(event);

            then(notificationRepository).should(never()).save(any());
            then(memberRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("LIKE 이벤트 → '님이 회원님의 게시물을 좋아합니다' 메시지로 저장")
        void like_event_saves_with_correct_message() {
            Member receiver = buildMember(1L, "receiver");
            Member sender = buildMember(2L, "koo");
            given(memberRepository.getReferenceById(1L)).willReturn(receiver);
            given(memberRepository.getReferenceById(2L)).willReturn(sender);

            NotificationEvent event = new NotificationEvent(
                    NotificationType.LIKE, 1L, 2L, 42L, null);

            handler.handleNotification(event);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            then(notificationRepository).should().save(captor.capture());

            Notification saved = captor.getValue();
            assertThat(saved.getType()).isEqualTo(NotificationType.LIKE);
            assertThat(saved.getReceiver()).isSameAs(receiver);
            assertThat(saved.getSender()).isSameAs(sender);
            assertThat(saved.getTargetId()).isEqualTo(42L);
            assertThat(saved.getMessage()).isEqualTo("koo님이 회원님의 게시물을 좋아합니다");
            assertThat(saved.isRead()).isFalse();
        }

        @Test
        @DisplayName("FOLLOW 이벤트 → '님이 회원님을 팔로우하기 시작했습니다' 메시지로 저장")
        void follow_event_saves_with_correct_message() {
            Member receiver = buildMember(3L, "target");
            Member sender = buildMember(4L, "follower");
            given(memberRepository.getReferenceById(3L)).willReturn(receiver);
            given(memberRepository.getReferenceById(4L)).willReturn(sender);

            NotificationEvent event = new NotificationEvent(
                    NotificationType.FOLLOW, 3L, 4L, null, null);

            handler.handleNotification(event);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            then(notificationRepository).should().save(captor.capture());

            Notification saved = captor.getValue();
            assertThat(saved.getType()).isEqualTo(NotificationType.FOLLOW);
            assertThat(saved.getTargetId()).isNull();
            assertThat(saved.getMessage()).isEqualTo("follower님이 회원님을 팔로우하기 시작했습니다");
        }

        @Test
        @DisplayName("COMMENT 이벤트 → '님이 댓글을 남겼습니다' 메시지로 저장")
        void comment_event_saves_with_correct_message() {
            Member receiver = buildMember(5L, "postOwner");
            Member sender = buildMember(6L, "commenter");
            given(memberRepository.getReferenceById(5L)).willReturn(receiver);
            given(memberRepository.getReferenceById(6L)).willReturn(sender);

            NotificationEvent event = new NotificationEvent(
                    NotificationType.COMMENT, 5L, 6L, 100L, null);

            handler.handleNotification(event);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            then(notificationRepository).should().save(captor.capture());

            assertThat(captor.getValue().getMessage()).isEqualTo("commenter님이 댓글을 남겼습니다");
        }

        @Test
        @DisplayName("MENTION 이벤트 → '님이 댓글에서 회원님을 언급했습니다' 메시지로 저장")
        void mention_event_saves_with_correct_message() {
            Member receiver = buildMember(7L, "mentioned");
            Member sender = buildMember(8L, "writer");
            given(memberRepository.getReferenceById(7L)).willReturn(receiver);
            given(memberRepository.getReferenceById(8L)).willReturn(sender);

            NotificationEvent event = new NotificationEvent(
                    NotificationType.MENTION, 7L, 8L, 200L, null);

            handler.handleNotification(event);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            then(notificationRepository).should().save(captor.capture());

            assertThat(captor.getValue().getMessage()).isEqualTo("writer님이 댓글에서 회원님을 언급했습니다");
            assertThat(captor.getValue().getTargetId()).isEqualTo(200L);
        }

        @Test
        @DisplayName("저장 중 예외 발생 시 전파하지 않는다 (로그만 남김)")
        void exception_does_not_propagate() {
            Member receiver = buildMember(1L, "receiver");
            Member sender = buildMember(2L, "sender");
            given(memberRepository.getReferenceById(1L)).willReturn(receiver);
            given(memberRepository.getReferenceById(2L)).willReturn(sender);
            given(notificationRepository.save(any())).willThrow(new RuntimeException("DB 에러"));

            NotificationEvent event = new NotificationEvent(
                    NotificationType.LIKE, 1L, 2L, 10L, null);

            // 예외가 전파되지 않아야 함
            handler.handleNotification(event);

            then(notificationRepository).should().save(any());
        }
    }
}
