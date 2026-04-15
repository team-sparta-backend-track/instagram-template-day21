package com.example.instagramclone.domain.post.infrastructure;

import com.example.instagramclone.domain.post.api.ProfilePostResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/**
 * PostRepository에 QueryDSL 기반 커스텀 피드 조회 쿼리를 추가하기 위한 인터페이스입니다.
 *
 * 현재 PostRepository에 @Query JPQL로 작성된 findAllWithImages()를
 * 아래 순서로 QueryDSL로 완전히 대체합니다.
 *
 *   1단계: 이 인터페이스에 같은 메서드 시그니처를 선언한다.
 *   2단계: PostRepositoryImpl에서 QueryDSL로 구현한다.
 *   3단계: PostRepository에서 @Query를 제거하고 이 인터페이스를 extend에 추가한다.
 *         → public interface PostRepository extends JpaRepository<Post, Long>, PostRepositoryCustom
 *   4단계: PostService는 변경 없이 그대로 동작한다. (리팩토링의 철칙!)
 */
public interface PostRepositoryCustom {


    /**
     * 특정 회원의 게시글을 최신순으로 페이징 조회합니다.
     * 프로필 페이지 그리드 API에서 사용합니다. (Day 15: 좋아요 수, 댓글 수 포함)
     */
    Slice<ProfilePostResponse> findAllByWriterId(Long writerId, Pageable pageable);

    /** 커서 기반 프로필 게시물 조회 */
    Slice<ProfilePostResponse> findAllByWriterIdByCursor(Long writerId, Long cursorId, int size);

    /**
     * 메인 피드: Post + writer fetchJoin + 로그인 회원의 좋아요 여부를 EXISTS 서브쿼리로 한 번에 조회 (과제: N+1 없음).
     */
    Slice<PostFeedRow> findFeedWithLiked(Pageable pageable, Long loginMemberId);

    /**
     * 커서 기반 메인 피드 조회.
     * 기존 findFeedWithLiked()에서 딱 2줄만 바뀝니다:
     *   ① .offset(pageable.getOffset()) 제거
     *   ② .where(ltCursorId(cursorId)) 추가
     */
    Slice<PostFeedRow> findFeedWithLikedByCursor(Long cursorId, int size, Long loginMemberId);

    /**
     * 특정 회원 게시물 목록(최신순 = id 내림차순) 기준 이전·다음 글 ID를 한 번에 조회합니다.
     * <p>prev: 같은 회원 글 중 현재 id보다 큰 id 중 최소값(더 최신), next: 더 작은 id 중 최대값(더 예전).
     * <p>구현은 DB 왕복 1회(단일 {@code SELECT}, 스칼라 서브쿼리 2개)이며,
     * {@code posts(member_id, id)} 복합 인덱스가 있으면 서브쿼리가 범위 스캔 후 조기 종료에 유리합니다.
     * <p>바깥 {@code FROM posts WHERE id = ?}는 행 앵커용이므로, {@code postId}가 없으면 {@code null}을 돌려줍니다.
     * 상세 조회 등에서 게시물을 먼저 로드한 뒤 호출하는 전제에 맞춥니다.
     */
    PrevNextPostIds findPrevAndNextPostIdByProfile(Long memberId, Long postId);
}
