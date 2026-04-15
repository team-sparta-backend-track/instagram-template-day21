package com.example.instagramclone.domain.notification.api;

import com.example.instagramclone.core.common.dto.ApiResponse;
import com.example.instagramclone.core.common.dto.SliceResponse;
import com.example.instagramclone.domain.notification.application.NotificationService;
import com.example.instagramclone.domain.notification.domain.NotificationType;
import com.example.instagramclone.domain.notification.dto.NotificationResponse;
import com.example.instagramclone.infrastructure.security.annotation.LoginUser;
import com.example.instagramclone.infrastructure.security.dto.LoginUserInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 내 알림 목록 조회.
     *
     * GET /api/notifications?size=20
     * GET /api/notifications?size=20&type=LIKE
     * GET /api/notifications?size=20&isRead=false&cursor=42
     */
    @GetMapping("/api/notifications")
    public ResponseEntity<ApiResponse<SliceResponse<NotificationResponse>>> getMyNotifications(
            @RequestParam(name = "type", required = false) NotificationType type,
            @RequestParam(name = "isRead", required = false) Boolean isRead,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @LoginUser LoginUserInfoDto loginUser) {

        int safeSize = Math.max(1, Math.min(size, 50));
        SliceResponse<NotificationResponse> response =
                notificationService.getMyNotifications(
                        loginUser.id(), type, isRead, cursor, safeSize);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 알림 읽음 처리.
     * PATCH /api/notifications/{id}/read
     */
    @PatchMapping("/api/notifications/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long id,
            @LoginUser LoginUserInfoDto loginUser) {

        notificationService.markAsRead(id, loginUser.id());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
