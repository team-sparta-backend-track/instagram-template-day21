package com.example.instagramclone.core.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FollowErrorCode implements ErrorCode {
    ALREADY_FOLLOWING(HttpStatus.BAD_REQUEST, "F001", "이미 팔로우한 사용자입니다."),
    FOLLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "F002", "팔로우 관계를 찾을 수 없습니다."),
    CANNOT_FOLLOW_SELF(HttpStatus.BAD_REQUEST, "F003", "자기 자신은 팔로우할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
