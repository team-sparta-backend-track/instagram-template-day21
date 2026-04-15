package com.example.instagramclone.domain.hashtag.api;

import com.example.instagramclone.core.common.dto.ApiResponse;
import com.example.instagramclone.core.common.dto.SliceResponse;
import com.example.instagramclone.domain.hashtag.application.HashtagService;
import com.example.instagramclone.domain.post.api.ProfilePostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 해시태그 피드 API — {@code GET /api/hashtags/{name}/posts} (프로필 그리드와 동일 {@link SliceResponse} 계약).
 */
@RestController
@RequiredArgsConstructor
public class HashtagController {

    private final HashtagService hashtagService;
   

    /**
     * 태그가 붙은 게시물 목록 (무한 스크롤 — {@code page},{@code size}, 정렬은 서버에서 id DESC 고정).
     */
    @GetMapping("/api/hashtags/{name}/posts")
    public ResponseEntity<ApiResponse<SliceResponse<ProfilePostResponse>>> getPostsByHashtag(
            @PathVariable String name,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "12") int size) {

        int safeSize = Math.max(1, Math.min(size, 50));
        SliceResponse<ProfilePostResponse> body = hashtagService.getPostsByHashtagByCursor(name, cursor, safeSize);
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /**
     * 해시태그 추천 Top N.
     * <p>예: {@code /api/hashtags/suggestions?prefix=맛&limit=5}
     */
    @GetMapping("/api/hashtags/suggestions")
    public ResponseEntity<ApiResponse<List<HashtagMetaResponse>>> getSuggestions(
            @RequestParam(name = "prefix", required = false) String prefix,
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        List<HashtagMetaResponse> body = hashtagService.getSuggestions(prefix, limit);
        return ResponseEntity.ok(ApiResponse.success(body));
    }


}
