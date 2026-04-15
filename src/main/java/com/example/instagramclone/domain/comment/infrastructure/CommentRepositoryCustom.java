package com.example.instagramclone.domain.comment.infrastructure;

import com.example.instagramclone.domain.comment.domain.Comment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.Map;

/**
 * 댓글 조회 전용 QueryDSL 커스텀 리포지토리 (FollowRepositoryCustom 과 동일한 위치·역할).
 *
 * <p>Day 14에서 다루는 쿼리:
 * <ul>
 *   <li>특정 게시글의 원댓글만 Slice (parent is null, 정렬·Pageable) + {@code replyCount} 는
 *       {@link #findRootCommentsWithReplyCountByPostId(Long, Pageable)} 한 쿼리(상관 서브쿼리)</li>
 *   <li>특정 원댓글 아래 대댓글만 Slice — {@link #findRepliesByRootComment(Long, Long, Pageable)}</li>
 *   <li>대댓글 API 선검증 — {@link #existsRootCommentForReplies(Long, Long)} (id + post + 원댓글 여부)</li>
 * </ul>
 */
public interface CommentRepositoryCustom {

    /**
     * 게시글의 원댓글 목록 (무한 스크롤 / Slice) + 각 원댓글의 대댓글 수.
     *
     * <p>구현: 상관 서브쿼리 {@code COUNT} 로 한 번에 조회 (배치 {@code IN + GROUP BY} 대안과 트레이드오프는
     * {@link RootCommentListRow} 주석 참고).
     */
    Slice<RootCommentListRow> findRootCommentsWithReplyCountByPostId(Long postId, Pageable pageable);

    /** 커서 기반 원댓글 목록 (id ASC → WHERE id > cursor) */
    Slice<RootCommentListRow> findRootCommentsWithReplyCountByPostIdByCursor(Long postId, Long cursorId, int size);

    /**
     * 원댓글에 달린 대댓글 목록 (더보기 / Slice).
     *
     * <p>조건: {@code post_id = postId} 이고 {@code parent_id = rootCommentId}.
     * 호출 전 {@link #existsRootCommentForReplies(Long, Long)} 로 원댓글·게시글 일치를 검증하는 것을 권장합니다.
     */
    Slice<Comment> findRepliesByRootComment(Long postId, Long rootCommentId, Pageable pageable);

    /** 커서 기반 대댓글 목록 (id ASC → WHERE id > cursor) */
    Slice<Comment> findRepliesByRootCommentByCursor(Long postId, Long rootCommentId, Long cursorId, int size);

    /**
     * {@code GET .../comments/{rootCommentId}/replies} 선검증용.
     * <p>{@code rootCommentId} 가 해당 {@code postId} 게시글에 속하고, {@code parent IS NULL} 인 원댓글인지 한 번에 판별합니다.
     * (다른 글의 댓글 id, 대댓글 id를 넣은 경우 모두 {@code false})
     */
    boolean existsRootCommentForReplies(Long postId, Long rootCommentId);

    /**
     * Day 15 Live Coding: 피드 카드 배치 메트릭 조회용
     * 주어진 게시글 ID 목록에 대해 각 게시글의 원댓글 개수를 Map 형태로 반환합니다.
     */
    Map<Long, Long> countCommentsByPostIds(List<Long> postIds);
}
