package com.example.instagramclone.core.exception;

import lombok.Getter;

/**
 * 회원(Member) 관련 비즈니스 로직에서 발생하는 예외를 처리하는 클래스입니다.
 * RuntimeException을 상속받아, 예외 발생 시 트랜잭션이 롤백되도록 합니다.
 * ErrorCode를 필드로 가지고 있어, 어떤 종류의 에러인지 명확하게 구분할 수 있습니다.
 */
@Getter
public class MemberException extends BusinessException {

    public MemberException(ErrorCode errorCode) {
        super(errorCode);
    }
}