package com.example.instagramclone.domain.notification.infrastructure;

import com.example.instagramclone.domain.notification.domain.NotificationType;
import com.example.instagramclone.domain.notification.dto.NotificationResponse;
import org.springframework.data.domain.Slice;

public interface NotificationRepositoryCustom {

    Slice<NotificationResponse> findNotifications(
            Long receiverId,
            NotificationType type,    // nullable — 필터
            Boolean isRead,           // nullable — 필터
            Long cursorId,            // nullable — 커서
            int size
    );
}
