package com.example.instagramclone.domain.post.infrastructure;

/**
 * 프로필 그리드 컨텍스트에서 상세 보기 시 이전/다음 게시물 식별자 쌍.
 */
public record PrevNextPostIds(Long prevPostId, Long nextPostId) {
}