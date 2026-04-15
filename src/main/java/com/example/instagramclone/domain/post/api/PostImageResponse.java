package com.example.instagramclone.domain.post.api;

import com.fasterxml.jackson.annotation.JsonProperty;

// 프론트엔드 규격에 맞추어 PostImage 응답 DTO를 레코드로 선언 (feed.js 166번 라인 참조)
public record PostImageResponse(
        @JsonProperty("image_id")
        Long id,
        String imageUrl,
        Integer imageOrder
) {

}
