package com.example.instagramclone.domain.post.api;

public record PostCreateResponse(
        Long postId
) {
    public static PostCreateResponse from(Long postId) {
        return new PostCreateResponse(postId);
    }
}
