package com.example.instagramclone.domain.notification.infrastructure;

import com.example.instagramclone.domain.notification.domain.Notification;
import com.example.instagramclone.domain.notification.domain.NotificationType;
import com.example.instagramclone.domain.notification.domain.QNotification;
import com.example.instagramclone.domain.notification.dto.NotificationResponse;
import com.example.instagramclone.domain.post.domain.QPostImage;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

@RequiredArgsConstructor
public class NotificationRepositoryCustomImpl implements NotificationRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private static final QNotification notification = QNotification.notification;
    private static final QPostImage postImage = QPostImage.postImage;

    @Override
    public Slice<NotificationResponse> findNotifications(
            Long receiverId, NotificationType type, Boolean isRead,
            Long cursorId, int size) {

        // targetId(postId)에 해당하는 게시물의 첫 번째 이미지(썸네일) 서브쿼리
        Expression<String> thumbnailExpr = JPAExpressions
                .select(postImage.imageUrl)
                .from(postImage)
                .where(
                        postImage.post.id.eq(notification.targetId),
                        postImage.imgOrder.eq(1)
                )
                .limit(1);

        List<Tuple> tuples = queryFactory
                .select(notification, thumbnailExpr)
                .from(notification)
                .join(notification.sender).fetchJoin()
                .where(
                        notification.receiver.id.eq(receiverId),   // 필수 조건
                        eqType(type),                              // 선택 조건
                        eqIsRead(isRead),                          // 선택 조건
                        ltCursorId(cursorId)                       // 커서 조건
                )
                .orderBy(notification.id.desc())   // 최신 알림부터
                .limit(size + 1L)
                .fetch();

        boolean hasNext = tuples.size() > size;
        if (hasNext) {
            tuples = tuples.subList(0, size);
        }

        List<NotificationResponse> content = tuples.stream()
                .map(t -> {
                    Notification n = t.get(notification);
                    String thumbnailUrl = t.get(1, String.class);
                    return NotificationResponse.from(n, thumbnailUrl);
                })
                .toList();

        return new SliceImpl<>(content, Pageable.unpaged(), hasNext);
    }

    /** type 필터: null이면 전체 */
    private BooleanExpression eqType(NotificationType type) {
        return type != null ? notification.type.eq(type) : null;
    }

    /** 읽음 필터: null이면 전체 */
    private BooleanExpression eqIsRead(Boolean isRead) {
        return isRead != null ? notification.isRead.eq(isRead) : null;
    }

    /** 커서 조건 */
    private BooleanExpression ltCursorId(Long cursorId) {
        return cursorId != null ? notification.id.lt(cursorId) : null;
    }
}
