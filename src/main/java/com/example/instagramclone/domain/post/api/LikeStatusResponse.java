package com.example.instagramclone.domain.post.api;

// 피드 좋아요 상태 응답 DTO (현재는 연습용으로 고정값 반환)
public record LikeStatusResponse(
        boolean liked,
        long likeCount
) {

}
