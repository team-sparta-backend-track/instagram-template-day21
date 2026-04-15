package com.example.instagramclone.domain.follow.api;

import com.example.instagramclone.domain.member.domain.Member;

/**
 * 팔로워 / 팔로잉 리스트 한 줄(row)에 해당하는 응답 DTO.
 *
 * 단순히 회원 기본 정보만 내려주는 것이 아니라,
 * "현재 로그인 사용자가 이 사람을 팔로우 중인가?" 와
 * "이 사람이 나 자신인가?" 같은 화면 제어용 상태값도 함께 담는다.
 *
 * 프론트엔드는 이 응답을 이용해
 * - 프로필 이미지
 * - 이름/아이디
 * - 팔로우 버튼 상태
 * - "나" 뱃지
 * 등을 한 번에 그릴 수 있다.
 */
public record FollowMemberResponse(
        /** 리스트에 표시되는 대상 유저의 PK */
        Long memberId,
        /** 화면에 노출할 사용자 아이디(고유 username) */
        String username,
        /** 프로필에 표시할 이름 */
        String name,
        /** 프로필 썸네일 이미지 URL */
        String profileImageUrl,
        /** 로그인 유저 기준으로, 이 사람을 이미 팔로우 중인지 여부 */
        boolean following,
        /** 리스트 항목의 주인공이 로그인 유저 자신인지 여부 ("나" 표시용) */
        boolean me
) {
    /**
     * Member 엔티티 + 화면 상태값(isFollowing, isMe)을 조합해
     * FollowMemberResponse 한 건을 생성하는 팩토리 메서드.
     */
    public static FollowMemberResponse of(Member member, boolean following, boolean me) {
        return new FollowMemberResponse(
                member.getId(),
                member.getUsername(),
                member.getName(),
                member.getProfileImageUrl(),
                following,
                me
        );
    }
}
