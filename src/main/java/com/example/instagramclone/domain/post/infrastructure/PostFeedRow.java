package com.example.instagramclone.domain.post.infrastructure;

import com.example.instagramclone.domain.post.domain.Post;

/**
 * QueryDSL 피드 조회 1쿼리 결과: 게시물 + 로그인 회원 기준 좋아요 여부 (EXISTS 서브쿼리).
 */
public record PostFeedRow(Post post, boolean liked) {
}
