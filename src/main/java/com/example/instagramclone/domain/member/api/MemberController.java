package com.example.instagramclone.domain.member.api;

import com.example.instagramclone.core.common.dto.ApiResponse;
import com.example.instagramclone.core.common.dto.SliceResponse;
import com.example.instagramclone.domain.member.application.MemberProfileService;
import com.example.instagramclone.domain.member.application.MemberService;
import com.example.instagramclone.infrastructure.security.annotation.LoginUser;
import com.example.instagramclone.infrastructure.security.dto.LoginUserInfoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MemberController {

    private final MemberProfileService memberProfileService;
    private final MemberService memberService;

    /**
     * username 기반 프로필 조회.
     *
     * 프론트의 /:username 라우트와 바로 연결하기 위한 엔드포인트.
     */
    @GetMapping("/api/profiles/{username}")
    public ResponseEntity<ApiResponse<MemberProfileResponse>> getProfileByUsername(
            @PathVariable String username,
            @LoginUser LoginUserInfoDto loginUser) {
        MemberProfileResponse response = memberProfileService.getProfileByUsername(loginUser.id(), username);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 커서 기반 유저 검색.
     *
     * GET /api/members/search?keyword=kuro&size=20          ← 첫 페이지
     * GET /api/members/search?keyword=kuro&size=20&cursor=42 ← 다음 페이지
     */
    @GetMapping("/api/members/search")
    public ResponseEntity<ApiResponse<SliceResponse<MemberSummary>>> searchUsers(
            @RequestParam(name = "keyword") String keyword,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        int safeSize = Math.max(1, Math.min(size, 50));
        SliceResponse<MemberSummary> response =
                memberService.searchUsersByCursor(keyword, cursor, safeSize);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
