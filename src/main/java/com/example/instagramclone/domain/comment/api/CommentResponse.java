package com.example.instagramclone.domain.comment.api;

import com.example.instagramclone.domain.comment.domain.Comment;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 댓글 목록 응답 (원댓글 목록에서 대댓글 수 포함).
 *
 * <p>대댓글 전용 조회 API에서는 {@code replyCount}를 null로 두거나 스펙에서 제외해도 됨 (팀 합의).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommentResponse(
        Long id,
        String content,
        @JsonProperty("member_id")
        Long memberId,
        String username,
        String profileImageUrl,
        /** 원댓글 목록에서만 채움. 대댓글 목록이면 null */
        Integer replyCount,
        LocalDateTime createdAt
) {
    /**
     * 저장 직후 또는 단건 조회용: 엔티티 → API 응답.
     *
     * <ul>
     *   <li>원댓글({@code parent == null}): 아직 목록 조회 전이므로 {@code replyCount}는 0으로 둠 (작성 직후 대댓글 없음).</li>
     *   <li>대댓글: 목록 스펙상 원댓글에만 replyCount를 붙이므로 {@code replyCount}는 null.</li>
     * </ul>
     */
    public static CommentResponse from(Comment comment) {
        boolean isRoot = comment.getParent() == null;
        Integer replyCount = isRoot ? 0 : null;
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getWriter().getId(),
                comment.getWriter().getUsername(),
                comment.getWriter().getProfileImageUrl(),
                replyCount,
                comment.getCreatedAt()
        );
    }

    /**
     * 원댓글 목록 조회 전용: 배치로 집계한 대댓글 수를 {@code replyCount}에 넣는다.
     *
     * @param replyCount 해당 원댓글 id를 {@code parent}로 갖는 대댓글 행 개수 (0 이상)
     */
    public static CommentResponse fromRootListItem(Comment comment, long replyCount) {
        int safe = replyCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) replyCount;
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getWriter().getId(),
                comment.getWriter().getUsername(),
                comment.getWriter().getProfileImageUrl(),
                safe,
                comment.getCreatedAt()
        );
    }
}
