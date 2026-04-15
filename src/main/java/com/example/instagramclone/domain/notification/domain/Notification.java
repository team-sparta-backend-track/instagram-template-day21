package com.example.instagramclone.domain.notification.domain;

import com.example.instagramclone.core.common.BaseEntity;
import com.example.instagramclone.domain.member.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notification_receiver", columnList = "receiver_id, created_at DESC"),
                @Index(name = "idx_notification_receiver_unread",
                        columnList = "receiver_id, is_read, created_at DESC")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    /** 알림을 받는 사람 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false)
    private Member receiver;

    /** 알림을 발생시킨 사람 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private Member sender;

    /**
     * 알림의 대상 ID.
     * - LIKE, COMMENT, MENTION → postId
     * - FOLLOW → null (팔로우는 대상이 사람이므로 sender로 충분)
     */
    @Column(name = "target_id")
    private Long targetId;

    /** 읽음 여부 — 기본값 false */
    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    /** 알림 메시지 (선택) — "koo님이 좋아요를 눌렀습니다" */
    @Column(length = 200)
    private String message;

    public static Notification create(NotificationType type, Member receiver,
                                       Member sender, Long targetId, String message) {
        Notification n = new Notification();
        n.type = type;
        n.receiver = receiver;
        n.sender = sender;
        n.targetId = targetId;
        n.message = message;
        return n;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
