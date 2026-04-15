package com.example.instagramclone.domain.notification.dto;

import com.example.instagramclone.domain.notification.domain.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
    Long notificationId,
    String type,           // "LIKE", "COMMENT", "FOLLOW", "MENTION"
    Long senderId,
    String senderUsername,
    String senderProfileImageUrl,
    Long targetId,         // postId (FOLLOW일 때 null)
    String targetThumbnailUrl, // 게시물 썸네일 (LIKE, COMMENT, MENTION만. FOLLOW는 null)
    String message,
    boolean isRead,
    LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n, String targetThumbnailUrl) {
        return new NotificationResponse(
                n.getId(),
                n.getType().name(),
                n.getSender().getId(),
                n.getSender().getUsername(),
                n.getSender().getProfileImageUrl(),
                n.getTargetId(),
                targetThumbnailUrl,
                n.getMessage(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
