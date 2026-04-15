package com.example.instagramclone.domain.comment.infrastructure;

import com.example.instagramclone.domain.comment.domain.Comment;
import com.example.instagramclone.domain.comment.domain.QComment;
import com.querydsl.core.Tuple;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.example.instagramclone.domain.comment.domain.QComment.comment;
import static com.querydsl.core.group.GroupBy.groupBy;

/**
 * {@link CommentRepositoryCustom}의 QueryDSL 구현체.
 *
 * <p>원댓글 목록은 {@code parent IS NULL}, 대댓글 수는 상관 서브쿼리 {@code COUNT} 로 같은 SELECT에 붙입니다
 * ({@link RootCommentListRow} 참고).
 */
@Repository
@RequiredArgsConstructor
public class CommentRepositoryCustomImpl implements CommentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /**
     * 특정 게시글의 원댓글만 조회하고, 각 행에 대댓글 수를 붙입니다. 작성자(Member)는 {@code fetchJoin}.
     *
     * <p>대댓글 수는 <strong>상관 서브쿼리</strong>로 계산합니다 (배치 {@code GROUP BY} 와의 선택은 팀·부하에 따라).
     *
     * <pre>
     * SELECT c.*, writer.*,
     *   (SELECT COUNT(r.id) FROM comments r WHERE r.parent_id = c.id) AS reply_count
     * FROM comments c
     * INNER JOIN member ... ON writer
     * WHERE c.post_id = ? AND c.parent_id IS NULL
     * ORDER BY c.created_at ASC, c.id ASC
     * LIMIT ...
     * </pre>
     *
     * <p>정렬: {@code createdAt ASC}, {@code id ASC}. Slice: {@code limit = pageSize + 1}.
     */
    @Override
    public Slice<RootCommentListRow> findRootCommentsWithReplyCountByPostId(Long postId, Pageable pageable) {
        QComment reply = new QComment("reply");

        // 상관 서브쿼리: JPQLSubQuery 타입이 NumberExpression 과 다를 수 있어 var 로 둠
        var replyCountExpr = JPAExpressions
                .select(reply.id.count())
                .from(reply)
                .where(reply.parent.id.eq(comment.id));

        List<Tuple> tuples = queryFactory
                .select(comment, replyCountExpr)
                .from(comment)
                .join(comment.writer).fetchJoin()
                .where(
                        comment.post.id.eq(postId),
                        comment.parent.isNull()
                )
                .orderBy(comment.createdAt.asc(), comment.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1L)
                .fetch();

        return toRootCommentSlice(tuples, pageable);
    }

    @Override
    public Slice<RootCommentListRow> findRootCommentsWithReplyCountByPostIdByCursor(Long postId, Long cursorId, int size) {
        QComment reply = new QComment("reply");

        var replyCountExpr = JPAExpressions
                .select(reply.id.count())
                .from(reply)
                .where(reply.parent.id.eq(comment.id));

        var query = queryFactory
                .select(comment, replyCountExpr)
                .from(comment)
                .join(comment.writer).fetchJoin()
                .where(
                        comment.post.id.eq(postId),
                        comment.parent.isNull()
                );

        if (cursorId != null) {
            query.where(comment.id.gt(cursorId));  // ASC 정렬이므로 gt
        }

        List<Tuple> tuples = query
                .orderBy(comment.createdAt.asc(), comment.id.asc())
                .limit(size + 1L)
                .fetch();

        return toRootCommentSliceByCursor(tuples, size);
    }

    @Override
    public Slice<Comment> findRepliesByRootCommentByCursor(Long postId, Long rootCommentId, Long cursorId, int size) {
        var query = queryFactory
                .selectFrom(comment)
                .join(comment.writer).fetchJoin()
                .where(
                        comment.post.id.eq(postId),
                        comment.parent.id.eq(rootCommentId)
                );

        if (cursorId != null) {
            query.where(comment.id.gt(cursorId));  // ASC 정렬이므로 gt
        }

        List<Comment> content = query
                .orderBy(comment.createdAt.asc(), comment.id.asc())
                .limit(size + 1L)
                .fetch();

        return toSliceByCursor(content, size);
    }

    /**
     * 대댓글 API 진입 전, 경로 변수 조합이 유효한지 한 방에 검사합니다.
     *
     * <pre>
     * SELECT 1
     * FROM comments c
     * WHERE c.id = :rootCommentId
     *   AND c.post_id = :postId
     *   AND c.parent_id IS NULL;
     * </pre>
     *
     * <p>대댓글 id를 넣거나, 다른 게시글에 달린 댓글 id를 넣으면 행이 없으므로 {@code false} 입니다.
     */
    @Override
    public boolean existsRootCommentForReplies(Long postId, Long rootCommentId) {
        if (rootCommentId == null) {
            return false;
        }
        Comment one = queryFactory
                .selectFrom(comment)
                .where(
                        comment.id.eq(rootCommentId),
                        comment.post.id.eq(postId),
                        comment.parent.isNull()
                )
                .fetchFirst();
        return one != null;
    }

    /**
     * 특정 원댓글({@code rootCommentId})에 매달린 대댓글만 조회합니다.
     *
     * <p>조건: {@code post_id = postId} 이고 {@code parent_id = rootCommentId}.
     * (같은 테이블에 원댓·대댓이 같이 있으므로, 반드시 게시글 id까지 넣어 다른 글의 동일 parent id 충돌을 막습니다.)
     *
     * <p>정렬: 원댓글 목록과 동일하게 <strong>대화가 위에서 아래로</strong> 읽히도록 {@code createdAt ASC}, 타이브레이크 {@code id ASC}.
     * Slice: {@code limit = pageSize + 1} 로 다음 페이지 존재 여부를 판별합니다.
     */
    @Override
    public Slice<Comment> findRepliesByRootComment(Long postId, Long rootCommentId, Pageable pageable) {
        List<Comment> content = queryFactory
                .selectFrom(comment)
                .join(comment.writer).fetchJoin()
                .where(
                        comment.post.id.eq(postId),
                        comment.parent.id.eq(rootCommentId)
                )
                .orderBy(comment.createdAt.asc(), comment.id.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1L)
                .fetch();

        return toSlice(content, pageable);
    }

    private static Slice<RootCommentListRow> toRootCommentSlice(List<Tuple> tuples, Pageable pageable) {
        boolean hasNext = tuples.size() > pageable.getPageSize();
        if (hasNext) {
            tuples = tuples.subList(0, pageable.getPageSize());
        }
        List<RootCommentListRow> rows = tuples.stream()
                .map(t -> {
                    Comment c = t.get(0, Comment.class);
                    Long cnt = t.get(1, Long.class);
                    long replyCount = cnt != null ? cnt : 0L;
                    return new RootCommentListRow(c, replyCount);
                })
                .toList();
        return new SliceImpl<>(rows, pageable, hasNext);
    }

    private static Slice<RootCommentListRow> toRootCommentSliceByCursor(List<Tuple> tuples, int size) {
        boolean hasNext = tuples.size() > size;
        if (hasNext) {
            tuples = tuples.subList(0, size);
        }
        List<RootCommentListRow> rows = tuples.stream()
                .map(t -> {
                    Comment c = t.get(0, Comment.class);
                    Long cnt = t.get(1, Long.class);
                    long replyCount = cnt != null ? cnt : 0L;
                    return new RootCommentListRow(c, replyCount);
                })
                .toList();
        return new SliceImpl<>(rows, Pageable.unpaged(), hasNext);
    }

    private static Slice<Comment> toSliceByCursor(List<Comment> items, int size) {
        boolean hasNext = items.size() > size;
        if (hasNext) {
            items = items.subList(0, size);
        }
        return new SliceImpl<>(items, Pageable.unpaged(), hasNext);
    }

    private static Slice<Comment> toSlice(List<Comment> items, Pageable pageable) {
        boolean hasNext = items.size() > pageable.getPageSize();
        if (hasNext) {
            items = items.subList(0, pageable.getPageSize());
        }
        return new SliceImpl<>(items, pageable, hasNext);
    }

    @Override
    public Map<Long, Long> countCommentsByPostIds(List<Long> postIds) {
        // IN 쿼리 + GROUP BY 로 postId별 댓글 수를 한 번에 집계한다.
        // 인스타그램 정책: 피드 카드에는 "원댓글 수"만 노출 (대댓글 제외) → parent IS NULL
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Long> result = queryFactory
                .from(comment)
                .where(
                        comment.post.id.in(postIds),
                        comment.parent.isNull()
                )
                .groupBy(comment.post.id)
                .transform(groupBy(comment.post.id).as(comment.id.count()));

                /*
                 * [대안] transform 대신 Tuple로 받아 Map으로 조립
                 *
                  List<Tuple> rows = queryFactory
                          .select(comment.post.id, comment.id.count())
                          .from(comment)
                          .where(
                                  comment.post.id.in(postIds),
                                  comment.parent.isNull()
                          )
                          .groupBy(comment.post.id)
                          .fetch();
                 
                  return rows.stream()
                          .collect(java.util.stream.Collectors.toMap(
                                  t -> t.get(0, Long.class),                 // post_id
                                  t ->  t.get(1, Long.class)       // count(comment.id)
                          ));
                 */
        return result != null ? result : Collections.emptyMap();
    }
}
