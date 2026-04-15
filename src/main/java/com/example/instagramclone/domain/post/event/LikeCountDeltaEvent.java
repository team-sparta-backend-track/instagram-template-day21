package com.example.instagramclone.domain.post.event;

public record LikeCountDeltaEvent(
    Long postId,
    int delta    // +1 (좋아요) 또는 -1 (좋아요 취소)
) {}
