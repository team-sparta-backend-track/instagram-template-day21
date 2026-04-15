package com.example.instagramclone.domain.follow.api;

import java.util.List;

public record FollowListResponse(
        List<FollowMemberResponse> users
) {
    public static FollowListResponse of(List<FollowMemberResponse> users) {
        return new FollowListResponse(users);
    }

    public static FollowListResponse empty() {
        return new FollowListResponse(List.of());
    }
}
