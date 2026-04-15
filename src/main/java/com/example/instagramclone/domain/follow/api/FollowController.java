package com.example.instagramclone.domain.follow.api;

import com.example.instagramclone.core.common.dto.ApiResponse;
import com.example.instagramclone.core.common.dto.SliceResponse;
import com.example.instagramclone.domain.follow.application.FollowService;
import com.example.instagramclone.infrastructure.security.annotation.LoginUser;
import com.example.instagramclone.infrastructure.security.dto.LoginUserInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    /** 로그인 유저가 대상 유저를 팔로우한다. */
    @PostMapping("/api/members/{memberId}/follow")
    public ResponseEntity<ApiResponse<FollowStatusResponse>> follow(
            @PathVariable Long memberId,
            @LoginUser LoginUserInfoDto loginUser) {
        FollowStatusResponse response = followService.follow(loginUser.id(), memberId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 로그인 유저가 대상 유저를 언팔로우한다. */
    @DeleteMapping("/api/members/{memberId}/follow")
    public ResponseEntity<ApiResponse<FollowStatusResponse>> unfollow(
            @PathVariable Long memberId,
            @LoginUser LoginUserInfoDto loginUser) {
        FollowStatusResponse response = followService.unfollow(loginUser.id(), memberId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 특정 유저를 팔로우하는 사람들 목록을 조회한다.
     * memberId = 프로필 주인, loginUser.id() = 현재 로그인한 사용자(응답의 isFollowing / isMe 계산 기준)
     * page / size 기반 Slice 페이징을 사용하며, 가장 최근에 팔로우한 사람이 먼저 내려간다.
     */
    @GetMapping("/api/members/{memberId}/followers")
    public ResponseEntity<ApiResponse<SliceResponse<FollowMemberResponse>>> getFollowers(
            @PathVariable Long memberId,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @LoginUser LoginUserInfoDto loginUser) {
        int safeSize = Math.max(1, Math.min(size, 50));
        SliceResponse<FollowMemberResponse> response = followService.getFollowersByCursor(loginUser.id(), memberId, cursor, safeSize);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 특정 유저가 팔로우하고 있는 사람들 목록을 조회한다.
     * followers 와 URL은 비슷하지만, Follow 조회 방향(from / to)이 반대라는 점이 핵심.
     * page / size 기반 Slice 페이징을 사용하며, 가장 최근에 생성된 팔로우 관계가 먼저 내려간다.
     */
    @GetMapping("/api/members/{memberId}/followings")
    public ResponseEntity<ApiResponse<SliceResponse<FollowMemberResponse>>> getFollowings(
            @PathVariable Long memberId,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @LoginUser LoginUserInfoDto loginUser) {
        int safeSize = Math.max(1, Math.min(size, 50));
        SliceResponse<FollowMemberResponse> response = followService.getFollowingsByCursor(loginUser.id(), memberId, cursor, safeSize);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
