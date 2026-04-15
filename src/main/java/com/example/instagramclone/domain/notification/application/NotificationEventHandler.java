package com.example.instagramclone.domain.notification.application;

import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import com.example.instagramclone.domain.notification.domain.Notification;
import com.example.instagramclone.domain.notification.domain.NotificationRepository;
import com.example.instagramclone.domain.notification.domain.NotificationType;
import com.example.instagramclone.domain.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 이벤트 핸들러.
 *
 * <p>{@code @TransactionalEventListener}: 트랜잭션 커밋 후에만 실행
 * <p>{@code @Async("notificationExecutor")}: Step 2에서 만든 전용 스레드 풀에서 실행
 *
 * <p>이 두 어노테이션의 조합으로:
 * <ol>
 *   <li>좋아요 트랜잭션이 롤백되면 → 이 메서드 실행 안 됨 (안전!)</li>
 *   <li>트랜잭션이 커밋되면 → 별도 스레드에서 알림 저장 (빠름!)</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventHandler {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;

    @Async("notificationExecutor")
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleNotification(NotificationEvent event) {
        // 자기 자신에 대한 알림은 무시
        if (event.isSelfNotification()) {
            log.debug("[알림 무시] 자기 자신에 대한 {} 알림 (senderId={})",
                    event.type(), event.senderId());
            return;
        }

        try {
            // 중복 알림 방지: 동일한 (type, receiver, sender, target)의 읽지 않은 알림이 이미 있으면 무시.
            // 좋아요 토글을 반복해도 알림이 1건만 쌓인다.
            boolean alreadyExists = notificationRepository
                    .existsByTypeAndReceiverIdAndSenderIdAndTargetIdAndIsReadFalse(
                            event.type(), event.receiverId(), event.senderId(), event.targetId());
            if (alreadyExists) {
                log.debug("[알림 중복] 이미 동일한 읽지 않은 알림 존재 (type={}, sender={}, target={})",
                        event.type(), event.senderId(), event.targetId());
                return;
            }

            Member receiver = memberRepository.getReferenceById(event.receiverId());
            Member sender = memberRepository.getReferenceById(event.senderId());

            String message = buildMessage(event.type(), sender);

            Notification notification = Notification.create(
                    event.type(),
                    receiver,
                    sender,
                    event.targetId(),
                    message
            );

            notificationRepository.save(notification);

            log.info("[알림 저장] {} → {} (type={}, targetId={})",
                    sender.getUsername(), event.receiverId(),
                    event.type(), event.targetId());

        } catch (Exception e) {
            // 알림 저장 실패가 원본 비즈니스 로직에 영향을 주면 안 됨
            log.error("[알림 저장 실패] type={}, receiver={}, sender={}: {}",
                    event.type(), event.receiverId(), event.senderId(), e.getMessage());
        }
    }

    private String buildMessage(NotificationType type, Member sender) {
        String username = sender.getUsername();
        return switch (type) {
            case LIKE -> username + "님이 회원님의 게시물을 좋아합니다";
            case FOLLOW -> username + "님이 회원님을 팔로우하기 시작했습니다";
            case COMMENT -> username + "님이 댓글을 남겼습니다";
            case MENTION -> username + "님이 댓글에서 회원님을 언급했습니다";
        };
    }
}
