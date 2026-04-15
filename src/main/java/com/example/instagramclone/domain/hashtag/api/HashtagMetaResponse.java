package com.example.instagramclone.domain.hashtag.api;


/**
 * 해시태그 이름과 게시물 수.
 *
 * <p>태그 상세(메타) API와 추천 목록 항목 등 동일 스펙이면 이 타입 하나로 재사용합니다.
 */
public record HashtagMetaResponse(
        String hashtagName,
        long postCount
) {
}
