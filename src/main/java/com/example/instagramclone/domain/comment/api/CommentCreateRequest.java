package com.example.instagramclone.domain.comment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글/대댓글 작성 요청.
 *
 * <p>{@code parentId}가 없으면 원댓글, 있으면 해당 id를 부모로 하는 대댓글.
 */
public record CommentCreateRequest(
        @NotBlank(message = "댓글 내용은 필수입니다.")
        @Size(max = 2000, message = "댓글은 2000자 이하여야 합니다.")
        String content,

        /** 대댓글일 때만 설정. 원댓글은 null 또는 생략 */
        Long parentId
) {
}
