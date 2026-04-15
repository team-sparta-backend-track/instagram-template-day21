package com.example.instagramclone.domain.notification.domain;

import com.example.instagramclone.domain.notification.infrastructure.NotificationRepositoryCustom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long>,
        NotificationRepositoryCustom {

    /**
     * 동일한 알림이 이미 읽지 않은 상태로 존재하는지 확인.
     * 좋아요 토글을 반복할 때 중복 알림 방지용.
     */
    boolean existsByTypeAndReceiverIdAndSenderIdAndTargetIdAndIsReadFalse(
            NotificationType type, Long receiverId, Long senderId, Long targetId);
}
