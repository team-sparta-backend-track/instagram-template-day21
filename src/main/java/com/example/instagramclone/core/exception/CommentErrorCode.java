package com.example.instagramclone.core.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 댓글 도메인 전용 오류 코드 (Day 14).
 */
@Getter
@RequiredArgsConstructor
public enum CommentErrorCode implements ErrorCode {

    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "댓글을 찾을 수 없습니다."),
    PARENT_NOT_ROOT_COMMENT(HttpStatus.BAD_REQUEST, "R002", "대댓글은 원댓글에만 달 수 있습니다."),
    INVALID_POST_FOR_COMMENT(HttpStatus.BAD_REQUEST, "R003", "댓글이 속한 게시글과 요청이 일치하지 않습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}
