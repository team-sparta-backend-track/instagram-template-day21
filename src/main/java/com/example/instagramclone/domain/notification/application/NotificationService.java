package com.example.instagramclone.domain.notification.application;

import com.example.instagramclone.core.common.dto.SliceResponse;
import com.example.instagramclone.core.exception.NotificationErrorCode;
import com.example.instagramclone.core.exception.NotificationException;
import com.example.instagramclone.domain.notification.domain.Notification;
import com.example.instagramclone.domain.notification.domain.NotificationRepository;
import com.example.instagramclone.domain.notification.domain.NotificationType;
import com.example.instagramclone.domain.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    /**
     * 내 알림 목록 조회 (필터 + 커서 페이지네이션).
     */
    public SliceResponse<NotificationResponse> getMyNotifications(
            Long loginMemberId, NotificationType type, Boolean isRead,
            Long cursorId, int size) {

        Slice<NotificationResponse> slice = notificationRepository.findNotifications(
                loginMemberId, type, isRead, cursorId, size);

        return SliceResponse.of(slice.hasNext(), slice.getContent());
    }

    /**
     * 알림 읽음 처리.
     */
    @Transactional
    public void markAsRead(Long notificationId, Long loginMemberId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationException(NotificationErrorCode.NOTIFICATION_NOT_FOUND));

        // 남의 알림을 읽음 처리하는 것 방지
        if (!notification.getReceiver().getId().equals(loginMemberId)) {
            throw new NotificationException(NotificationErrorCode.NOTIFICATION_ACCESS_DENIED);
        }

        notification.markAsRead();
    }
}
