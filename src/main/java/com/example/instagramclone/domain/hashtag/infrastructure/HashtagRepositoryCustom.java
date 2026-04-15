package com.example.instagramclone.domain.hashtag.infrastructure;

import com.example.instagramclone.domain.post.api.ProfilePostResponse;
import com.example.instagramclone.domain.hashtag.api.HashtagMetaResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;

/**
 * 해시태그 관련 QueryDSL 조회 (JPQL {@code @Query} 문자열 사용하지 않음).
 *
 * <p>태그 피드는 프로필 그리드와 동일하게 {@link com.example.instagramclone.domain.post.infrastructure.PostGridQueryHelper}로
 * Tuple·Slice를 조립하고, 이 Custom 구현은 조인·WHERE(태그명)만 다릅니다.
 */
public interface HashtagRepositoryCustom {


    /**
     * 태그가 붙은 게시물만 프로필 그리드와 동일 스펙으로 Slice 조회 (정렬 {@code post.id DESC}·썸네일·댓글 수·likeCount).
     */
    Slice<ProfilePostResponse> findProfilePostSliceByHashtagName(String normalizedHashtagName, Pageable pageable);

    /** 커서 기반 해시태그 게시물 조회 */
    Slice<ProfilePostResponse> findProfilePostSliceByHashtagNameByCursor(String normalizedHashtagName, Long cursorId, int size);

    /**
     * 해시태그 추천 Top N 목록 조회 (정렬: {@code postCount DESC, name ASC}).
     *
     * <p>prefix 가 비어있으면 전체 Top N을 조회합니다.
     *
     * @param prefix 정규화된 해시태그 prefix (예: {@code "맛"} or {@code "cafe"}), null/blank 허용
     * @param limit 최대 개수
     */
    List<HashtagMetaResponse> findTopSuggestions(String prefix, int limit);
}
