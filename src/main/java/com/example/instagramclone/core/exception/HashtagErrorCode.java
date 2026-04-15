package com.example.instagramclone.core.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HashtagErrorCode implements ErrorCode {
    HASHTAG_NOT_FOUND(HttpStatus.NOT_FOUND, "H001", "해시태그를 찾을 수 없습니다."),
    INVALID_HASHTAG_TOKEN(HttpStatus.BAD_REQUEST, "H002", "허용되지 않는 해시태그 형식입니다."),
    TOO_MANY_HASHTAGS(HttpStatus.BAD_REQUEST, "H003", "게시물에 붙일 수 있는 해시태그 개수를 초과했습니다."),
    HASHTAG_NAME_TOO_LONG(HttpStatus.BAD_REQUEST, "H004", "해시태그 이름이 너무 깁니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
