package com.example.instagramclone.domain.follow.api;

/**
 * 팔로우/언팔로우 "행위" 직후의 최소 응답 DTO.
 *
 * 프로필 헤더 전체 데이터(name, profileImageUrl, followingCount 등)는
 * 별도 프로필 조회 API에서 내려주고,
 * 이 응답은 버튼 상태와 팔로워 수 즉시 갱신에 필요한 값만 담는다.
 */
public record FollowStatusResponse(
        Long memberId,
        boolean following,
        long followerCount
) {
    public static FollowStatusResponse of(Long memberId, boolean following, long followerCount) {
        return new FollowStatusResponse(memberId, following, followerCount);
    }
}
