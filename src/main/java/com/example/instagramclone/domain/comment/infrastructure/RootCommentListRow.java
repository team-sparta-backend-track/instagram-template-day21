package com.example.instagramclone.domain.comment.infrastructure;

import com.example.instagramclone.domain.comment.domain.Comment;

/**
 * 원댓글 목록 1쿼리 결과: {@link Comment} + 해당 원댓글에 매달린 대댓글 수.
 *
 * <p>배치 {@code GROUP BY parent_id} 대신, 메인 SELECT에
 * <strong>상관 서브쿼리 {@code (SELECT COUNT(*) FROM comments WHERE parent_id = root.id)}</strong> 를 붙여
 * 한 번의 라운드트립으로 가져옵니다.
 *
 * <p><b>실행 계획·트레이드오프 (강의용 요약)</b>
 * <ul>
 *   <li><b>장점</b>: 애플리케이션에서 2번째 집계 쿼리 없음, 네트워크/라운드트립 1회.</li>
 *   <li><b>단점</b>: 원댓글마다 서브쿼리가 붙는 형태라 옵티마이저·인덱스에 따라
 *       배치 {@code IN + GROUP BY} 보다 느릴 수 있음 — {@code parent_id} 인덱스가 중요.</li>
 *   <li><b>가독성</b>: 서비스는 맵 병합 없이 행 단위로 DTO 조립 가능.</li>
 * </ul>
 */
public record RootCommentListRow(Comment comment, long replyCount) {
}
