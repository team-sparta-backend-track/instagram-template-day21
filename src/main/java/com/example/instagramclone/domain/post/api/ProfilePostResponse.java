package com.example.instagramclone.domain.post.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 프로필 페이지 게시글 그리드용 응답 DTO.
 *
 * 메인 피드의 PostResponse와 목적이 다릅니다.
 * - PostResponse: 피드에서 게시글 전체 정보(작성자, 전체 이미지, 내용)를 보여줄 때
 * - ProfilePostResponse: 프로필 그리드에서 썸네일만 표시하고 호버 시 좋아요/댓글 수를 보여줄 때
 */
public record ProfilePostResponse(
        @JsonProperty("post_id")
        Long postId,
        String thumbnailUrl,
        boolean multipleImages,
        long likeCount,
        long commentCount
) {
}
