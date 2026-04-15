package com.example.instagramclone.domain.post.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

// 프론트엔드 스펙에 맞추어 피드 응답 DTO 작성 (feed.js 116번 라인 참조)
public record PostResponse(
        @JsonProperty("feed_id")
        Long id,
        String content,
        String username,
        String profileImageUrl,
        List<PostImageResponse> images,
        LocalDateTime createdAt,
        LikeStatusResponse likeStatus,
        long commentCount,
        List<String> hashtagNames
) {

}
