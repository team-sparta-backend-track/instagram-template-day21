package com.example.instagramclone.domain.comment.api;

import com.example.instagramclone.core.common.dto.ApiResponse;
import com.example.instagramclone.core.common.dto.SliceResponse;
import com.example.instagramclone.domain.comment.application.CommentService;
import com.example.instagramclone.infrastructure.security.annotation.LoginUser;
import com.example.instagramclone.infrastructure.security.dto.LoginUserInfoDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 게시글 댓글 API (Day 14).
 *
 * <p>경로·응답 형식은 {@link com.example.instagramclone.domain.post.api.PostController} 와 맞춥니다.
 */
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    /**
     * 댓글 또는 대댓글 작성.
     */
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request,
            @LoginUser LoginUserInfoDto loginUser) {

        CommentResponse response = commentService.createComment(postId, request, loginUser.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    /**
     * 원댓글 목록 (무한 스크롤, 각 항목에 replyCount).
     *
     * <p>실제 정렬은 QueryDSL에서 {@code createdAt ASC, id ASC} 로 고정 — 댓글은 최신 피드와 달리 대화 순서가 자연스럽습니다.
     */
    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<SliceResponse<CommentResponse>>> getRootComments(
            @PathVariable Long postId,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @LoginUser LoginUserInfoDto loginUser) {

        int safeSize = Math.max(1, Math.min(size, 50));
        SliceResponse<CommentResponse> response = commentService.getRootCommentsByCursor(postId, cursor, safeSize, loginUser.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 특정 원댓글의 대댓글 목록 (답글 더보기).
     *
     * <p>인스타그램 Graph API 의 {@code /{comment-id}/replies} 와 같이, 펼쳤을 때만 별도 요청으로 가져옵니다.
     * 같은 {@code page} 를 올려 무한 스크롤합니다.
     */
    @GetMapping("/api/posts/{postId}/comments/{rootCommentId}/replies")
    public ResponseEntity<ApiResponse<SliceResponse<CommentResponse>>> getReplies(
            @PathVariable Long postId,
            @PathVariable Long rootCommentId,
            @RequestParam(name = "cursor", required = false) Long cursor,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @LoginUser LoginUserInfoDto loginUser) {

        int safeSize = Math.max(1, Math.min(size, 50));
        SliceResponse<CommentResponse> response = commentService.getRepliesByCursor(postId, rootCommentId, cursor, safeSize, loginUser.id());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
