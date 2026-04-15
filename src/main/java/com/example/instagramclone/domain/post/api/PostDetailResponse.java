package com.example.instagramclone.domain.post.api;

import com.example.instagramclone.domain.member.api.MemberSummary;
import com.example.instagramclone.domain.post.domain.Post;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 피드 상세 조회 응답 DTO
 *
 * <p>댓글 목록은 별도 API 전제. {@code hashtagNames} 는 조회 단계에서 배치/단건으로 채웁니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostDetailResponse(
        Long postId,
        String content,
        MemberSummary writer,
        List<String> imageUrls,
        LocalDateTime createdAt,
        LikeStatusResponse likeStatus,
        Long prevPostId,
        Long nextPostId,
        List<String> hashtagNames
) {

    public static PostDetailResponse of(
            Post post,
            MemberSummary writer,
            List<String> imageUrls,
            LocalDateTime createdAt,
            LikeStatusResponse likeStatus,
            Long prevPostId,
            Long nextPostId,
            List<String> hashtagNames
    ) {
        return new PostDetailResponse(
                post.getId(),
                post.getContent(),
                writer,
                imageUrls,
                createdAt,
                likeStatus,
                prevPostId,
                nextPostId,
                hashtagNames
        );
    }
}
