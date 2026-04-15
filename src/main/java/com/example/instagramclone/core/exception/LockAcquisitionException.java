package com.example.instagramclone.core.exception;

/**
 * [Day 18] 분산 락 획득에 실패했을 때 던지는 예외.
 *
 * <p>Redis(또는 DB) 락을 얻지 못하면 "현재 다른 요청이 처리 중"이라는 의미이므로
 * 클라이언트에게 {@code 409 Conflict} 또는 {@code 503 Service Unavailable}을 반환하는 것이 일반적이다.
 *
 * <p>기존 {@link BusinessException}과 분리한 이유:
 * 락 실패는 "비즈니스 규칙 위반"이 아니라 "동시성 충돌"이므로
 * 별도 계층(인프라 레벨)으로 분류해 두었다.
 * 필요하다면 {@link BusinessException}을 상속하도록 바꿔도 좋다.
 */
public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(String message) {
        super(message);
    }

    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}
