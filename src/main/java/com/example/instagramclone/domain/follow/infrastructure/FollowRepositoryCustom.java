package com.example.instagramclone.domain.follow.infrastructure;

import com.example.instagramclone.domain.follow.api.FollowMemberResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/**
 * Follow 리스트 조회를 QueryDSL로 최적화하기 위한 커스텀 리포지토리.
 *
 * 팔로워/팔로잉 목록 화면은 단순히 회원 목록만 필요한 것이 아니라,
 * 각 회원마다 "로그인 유저가 이 사람을 팔로우 중인가?"까지 함께 내려줘야 한다.
 * 이 값을 서비스에서 따로 계산하면 추가 쿼리가 필요하므로,
 * QueryDSL select 절에서 FollowMemberResponse로 바로 projection 한다.
 */
public interface FollowRepositoryCustom {

    /**
     * 특정 유저의 팔로워 목록을 최신순 Slice로 조회한다.
     * 각 row에는 following / me 값이 함께 계산되어 있다.
     */
    Slice<FollowMemberResponse> findFollowersWithStatus(Long profileOwnerId, Long loginMemberId, Pageable pageable);

    /**
     * 특정 유저의 팔로잉 목록을 최신순 Slice로 조회한다.
     * 각 row에는 following / me 값이 함께 계산되어 있다.
     */
    Slice<FollowMemberResponse> findFollowingsWithStatus(Long profileOwnerId, Long loginMemberId, Pageable pageable);

    /** 커서 기반 팔로워 목록 조회 (follow.id DESC) */
    Slice<FollowMemberResponse> findFollowersWithStatusByCursor(Long profileOwnerId, Long loginMemberId, Long cursorId, int size);

    /** 커서 기반 팔로잉 목록 조회 (follow.id DESC) */
    Slice<FollowMemberResponse> findFollowingsWithStatusByCursor(Long profileOwnerId, Long loginMemberId, Long cursorId, int size);
}
