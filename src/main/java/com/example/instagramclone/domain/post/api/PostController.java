package com.example.instagramclone.domain.post.api;

import com.example.instagramclone.core.common.dto.ApiResponse;
import com.example.instagramclone.core.common.dto.SliceResponse;
import com.example.instagramclone.core.util.PageableUtil;
import com.example.instagramclone.infrastructure.security.annotation.LoginUser;
import com.example.instagramclone.domain.post.application.PostLikeService;
import com.example.instagramclone.domain.post.application.PostService;
import com.example.instagramclone.infrastructure.security.dto.LoginUserInfoDto;
import org.springframework.data.domain.Pageable;


import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PostLikeService postLikeService;

    @PostMapping("/api/posts")
    public ResponseEntity<ApiResponse<PostCreateResponse>> createPost(
            @RequestPart("feed") PostCreateRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images,
            @LoginUser LoginUserInfoDto loginUser) throws IOException {

        // 필터가 앞에서 다 막아주기 때문에,
        Long postId = postService.create(request, images, loginUser.id());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(PostCreateResponse.from(postId)));
    }

    @GetMapping("/api/posts")
    public ResponseEntity<ApiResponse<SliceResponse<PostResponse>>> getFeed(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "5") int size,
            // 인증 데이터가 필요 없는 Public 피드일 경우 LoginUserArgumentResolver가 null을 반환하도록 설계했습니다.
            @LoginUser LoginUserInfoDto loginUser) {

        // 파라미터 검증 및 Pageable 생성 (관심사 분리)
        Pageable pageable = PageableUtil.createSafePageableDesc(page, size, "id");

        SliceResponse<PostResponse> response = postService.getFeed(pageable, loginUser.id());

        return ResponseEntity.ok(ApiResponse.success(response));
    }


    /**
     * 커서 기반 피드 API.
     *
     * GET /api/posts/cursor?size=5          ← 첫 페이지 (cursor 없음)
     * GET /api/posts/cursor?size=5&cursor=42 ← 두 번째 페이지 (마지막 id=42)
     */
    @GetMapping("/api/posts/cursor")
    public ResponseEntity<ApiResponse<SliceResponse<PostResponse>>> getFeedByCursor(
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "5") int size,
            @LoginUser LoginUserInfoDto loginUser) {

        // size 방어 (PageableUtil처럼 최소·최대 제한)
        int safeSize = Math.max(1, Math.min(size, 50));

        SliceResponse<PostResponse> response =
                postService.getFeedByCursor(cursor, safeSize, loginUser.id());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * username 기반 프로필 게시글 목록 조회.
     *
     * 프론트 라우트가 /:username 이므로 프로필 진입 시 바로 사용하기 좋다.
     */
    @GetMapping("/api/profiles/{username}/posts")
    public ResponseEntity<ApiResponse<SliceResponse<ProfilePostResponse>>> getProfilePostsByUsername(
            @PathVariable String username,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "12") int size) {

        int safeSize = Math.max(1, Math.min(size, 50));
        SliceResponse<ProfilePostResponse> response = postService.getMemberPostsByUsernameByCursor(username, cursor, safeSize);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 좋아요 토글 — 응답 liked·likeCount(비정규화) */
    @PostMapping("/api/posts/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeStatusResponse>> toggleLike(
            @PathVariable Long postId,
            @LoginUser LoginUserInfoDto loginUser) {
        LikeStatusResponse response = postLikeService.toggleLikeWriteBack(loginUser.id(), postId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Day 15 Live Coding: 피드 상세 조회 (선택적 네비게이션)
     * 댓글은 별도 API 로 분리하고 본문, 캐러셀, 작성자와 이전/다음 글 식별자를 반환한다.
     */
    @GetMapping("/api/posts/{postId}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPostDetail(
            @PathVariable Long postId,
            @RequestParam(required = false) String context,  // 예: "profile" 또는 "feed"
            @LoginUser LoginUserInfoDto loginUser) {
        PostDetailResponse response = postService.getPostDetail(postId, context, loginUser.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
