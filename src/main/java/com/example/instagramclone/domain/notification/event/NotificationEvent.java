package com.example.instagramclone.domain.notification.event;

import com.example.instagramclone.domain.notification.domain.NotificationType;

public record NotificationEvent(
    NotificationType type,
    Long receiverId,      // 알림 받는 사람
    Long senderId,        // 알림 발생시킨 사람
    Long targetId,        // 대상 ID (postId 등)
    String message        // 알림 메시지
) {
    /**
     * 자기 자신에 대한 알림은 의미 없으므로 필터링.
     * 내가 내 게시물에 좋아요 → 알림 X
     */
    public boolean isSelfNotification() {
        return receiverId.equals(senderId);
    }
}
