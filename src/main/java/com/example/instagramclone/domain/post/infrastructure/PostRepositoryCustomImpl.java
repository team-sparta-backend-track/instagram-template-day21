package com.example.instagramclone.domain.post.infrastructure;

import com.example.instagramclone.domain.post.api.ProfilePostResponse;
import com.example.instagramclone.domain.post.domain.Post;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.example.instagramclone.domain.post.domain.QPost.*;
import static com.example.instagramclone.domain.post.domain.QPostLike.*;

/**
 * PostRepositoryCustom의 QueryDSL 구현체입니다.
 * 기존 @Query JPQL 피드 조회 쿼리를 타입 세이프한 QueryDSL 코드로 대체합니다.
 *
 * [네이밍 컨벤션 필수]
 * Spring Data JPA는 fragment 인터페이스명 + "Impl" 접미사로 구현체를 탐색합니다.
 * PostRepositoryCustom → PostRepositoryCustomImpl (같은 패키지에 위치해야 함)
 *
 * [JPQL → QueryDSL 변환]
 * JPQL:    "SELECT p FROM Post p JOIN FETCH p.writer"
 * QueryDSL: post.writer 를 fetchJoin() 으로 연결 → 컴파일 타임에 오타 검증 가능
 */
@Repository
@RequiredArgsConstructor
public class PostRepositoryCustomImpl implements PostRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final PostGridQueryHelper postGridQueryHelper;

    /**
     * 특정 회원의 게시글을 최신순으로 페이징 조회합니다.
     * <p>
     * Tuple·서브쿼리·Slice 조립은 {@link PostGridQueryHelper}에 위임합니다.
     */
    @Override
    public Slice<ProfilePostResponse> findAllByWriterId(Long writerId, Pageable pageable) {
        JPQLQuery<Post> baseQuery = queryFactory
                .selectFrom(post)
                .where(post.writer.id.eq(writerId))
                .orderBy(post.id.desc());

        return postGridQueryHelper.findProfilePostSlice(baseQuery, pageable);
    }

    /**
     * 메인 피드: Post + writer fetchJoin + 로그인 회원 기준 liked.
     *
     * <p><b>현재 구현 — EXISTS</b> (아래 쿼리와 동등한 의미)</p>
     * <pre>
     * SELECT p.*, EXISTS (
     *   SELECT 1 FROM post_like pl
     *   WHERE pl.post_id = p.id AND pl.member_id = ?
     * ) AS liked
     * FROM posts p INNER JOIN member ... (writer fetch)
     * ORDER BY p.id DESC
     * </pre>
     * Post 행이 늘지 않아 조인/fetch 확장 시에도 안전함.
     *
     * <p><b>대안 — LEFT JOIN + id IS NOT NULL (수업·실무에서 동일 논리로 자주 씀)</b></p>
     * PostLike를 한 유저·한 글당 최대 1행으로 두는 전제(복합 유니크)에서,
     * 조인에 걸리면 true, 안 걸리면 false를 DB가 바로 뽑아줄 수 있음.
     * <pre>
     * // QueryDSL 예시 (구현은 EXISTS 유지)
     * queryFactory
     *     .select(post, postLike.id.isNotNull())   // liked = 조인 성공 여부
     *     .from(post)
     *     .join(post.writer).fetchJoin()
     *     .leftJoin(postLike).on(
     *         postLike.post.eq(post).and(postLike.member.id.eq(loginMemberId))
     *     )
     *     .orderBy(post.id.desc())
     *     ...
     * </pre>
     * 주의: 같은 쿼리에서 PostImage 등으로 행이 늘어나는 join과 섞으면 post당 여러 행이 될 수 있음.
     * 그때는 DISTINCT 또는 EXISTS 쪽이 단순함. 지금처럼 writer만 fetch하면 LEFT JOIN PostLike는 post당 1행 유지.
     */
    @Override
    public Slice<PostFeedRow> findFeedWithLiked(Pageable pageable, Long loginMemberId) {

        // EXISTS 방식: semi join, 결과 행 수 = Post 행 수

        BooleanExpression likedExpr = JPAExpressions
                .selectFrom(postLike)
                .where(postLike.post.eq(post).and(postLike.member.id.eq(loginMemberId)))
                .exists();

        List<Tuple> tuples = queryFactory
                .select(post, likedExpr)
                .from(post)
                .join(post.writer).fetchJoin()
                .orderBy(post.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1L)
                .fetch();


        boolean hasNext = tuples.size() > pageable.getPageSize();
        if (hasNext) {
            tuples = tuples.subList(0, pageable.getPageSize());
        }

        List<PostFeedRow> rows = tuples.stream()
                .map(t -> new PostFeedRow(
                        t.get(post),
                        toBoolean(t.get(likedExpr))))
                .toList();

        return new SliceImpl<>(rows, pageable, hasNext);
    }

    private static boolean toBoolean(Object o) {
        if (o == null) {
            return false;
        }
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0;
        }
        return false;
    }

    @Override
    public Slice<ProfilePostResponse> findAllByWriterIdByCursor(Long writerId, Long cursorId, int size) {
        JPQLQuery<Post> baseQuery = queryFactory
                .selectFrom(post)
                .where(post.writer.id.eq(writerId))
                .orderBy(post.id.desc());

        return postGridQueryHelper.findProfilePostSliceByCursor(baseQuery, cursorId, size);
    }

    /**
     * 커서 기반 피드 조회.
     * 기존 findFeedWithLiked()에서 딱 2줄만 바뀝니다:
     *   ① .offset(pageable.getOffset()) 제거
     *   ② .where(ltCursorId(cursorId)) 추가
     */
    @Override
    public Slice<PostFeedRow> findFeedWithLikedByCursor(Long cursorId, int size, Long loginMemberId) {

        BooleanExpression likedExpr = JPAExpressions
                .selectFrom(postLike)
                .where(postLike.post.eq(post).and(postLike.member.id.eq(loginMemberId)))
                .exists();

        List<Tuple> tuples = queryFactory
                .select(post, likedExpr)
                .from(post)
                .join(post.writer).fetchJoin()
                .where(ltCursorId(cursorId))          // ✅ OFFSET 대신 WHERE 조건
                .orderBy(post.id.desc())
                // .offset() 없음!                    // ✅ offset 제거
                .limit(size + 1L)                     // hasNext 판단용 +1은 동일
                .fetch();

        boolean hasNext = tuples.size() > size;
        if (hasNext) {
            tuples = tuples.subList(0, size);
        }

        List<PostFeedRow> rows = tuples.stream()
                .map(t -> new PostFeedRow(
                        t.get(post),
                        toBoolean(t.get(likedExpr))))
                .toList();

        return new SliceImpl<>(rows, Pageable.unpaged(), hasNext);
    }

    /**
     * 커서 조건: cursorId가 null이면 null 반환 → where()가 무시 (= 첫 페이지)
     *
     * QueryDSL 동적 쿼리 패턴 — null을 반환하면 where()에서 해당 조건이 자동으로 무시됩니다.
     */
    private BooleanExpression ltCursorId(Long cursorId) {
        return cursorId != null ? post.id.lt(cursorId) : null;
    }

    @Override
    public PrevNextPostIds findPrevAndNextPostIdByProfile(Long memberId, Long postId) {
        Long prevPostId = queryFactory
                .select(post.id)
                .from(post)
                .where(
                        post.writer.id.eq(memberId),
                        post.id.gt(postId)
                )
                .orderBy(post.id.asc())
                .fetchFirst();

        Long nextPostId = queryFactory
                .select(post.id)
                .from(post)
                .where(
                        post.writer.id.eq(memberId),
                        post.id.lt(postId)
                )
                .orderBy(post.id.desc())
                .fetchFirst();

        return new PrevNextPostIds(prevPostId, nextPostId);
    }
}
