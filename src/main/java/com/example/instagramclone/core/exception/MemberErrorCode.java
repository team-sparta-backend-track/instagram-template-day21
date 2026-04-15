package com.example.instagramclone.core.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {
    DUPLICATE_EMAIL(HttpStatus.BAD_REQUEST, "M001", "이미 존재하는 이메일입니다."),
    DUPLICATE_USERNAME(HttpStatus.BAD_REQUEST, "M002", "이미 존재하는 사용자 이름입니다."),
    DUPLICATE_PHONE(HttpStatus.BAD_REQUEST, "M003", "이미 존재하는 전화번호입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M004", "회원을 찾을 수 없습니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "M005", "아이디 또는 비밀번호가 일치하지 않습니다."),
    UNAUTHORIZED_ACCESS(HttpStatus.UNAUTHORIZED, "M006", "로그인이 필요한 서비스입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "M007", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "M008", "만료된 토큰입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
