package com.example.instagramclone.core.aop.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 로깅 시 민감한 정보(예: 비밀번호)를 마스킹 처리하기 위한 어노테이션입니다.
 * DTO 내부의 필드에 선언하여 사용합니다.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Masking {

    // [과제 2 예시답안] 기본값을 PASSWORD로 설정하여 기존 코드의 수정 영향을 최소화합니다.
    MaskingType type() default MaskingType.PASSWORD;
}
