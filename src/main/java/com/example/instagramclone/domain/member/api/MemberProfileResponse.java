package com.example.instagramclone.domain.member.api;

import com.example.instagramclone.domain.member.domain.Member;

/**
 * 프로필 1건 조회 응답 DTO.
 *
 * Day 15에서 프로필 헤더 통계(팔로워/팔로잉/게시물 수)까지 확장됩니다.
 */
public record MemberProfileResponse(
        Long memberId,
        String username,
        String name,
        String profileImageUrl,
        long followerCount,
        long followingCount,
        long postCount,
        boolean isFollowing,
        boolean isCurrentUser
) {


    public static MemberProfileResponse of(
            Member member,
            long followerCount,
            long followingCount,
            long postCount,
            boolean isFollowing,
            boolean isCurrentUser
    ) {
        return new MemberProfileResponse(
                member.getId(),
                member.getUsername(),
                member.getName(),
                member.getProfileImageUrl(),
                followerCount,
                followingCount,
                postCount,
                isFollowing,
                isCurrentUser
        );
    }
}
