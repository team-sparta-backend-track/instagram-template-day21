package com.example.instagramclone.core.exception;

import org.springframework.http.HttpStatus;

/**
 * 애플리케이션에서 발생할 수 있는 에러들을 정의한 공통 인터페이스입니다.
 */
public interface ErrorCode {
    HttpStatus getStatus();
    String getCode();
    String getMessage();
}
