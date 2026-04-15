package com.example.instagramclone.core.aop.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * [Day 18 과제] Redis 분산 락을 어노테이션으로 선언하는 커스텀 어노테이션.
 *
 * <p>사용 예시:
 * <pre>
 *   {@literal @}DistributedLock(key = "'lock:like:' + #postId")
 *   {@literal @}Transactional
 *   public LikeStatusResponse toggleLike(Long loginMemberId, Long postId) {
 *       // 비즈니스 로직만!
 *   }
 * </pre>
 *
 * <p><b>key — SpEL(Spring Expression Language) 표현식</b><br>
 * {@code @Cacheable(key = "#id")}와 완전히 같은 문법이다.
 * 메서드 파라미터 이름을 {@code #파라미터명}으로 참조할 수 있다.
 * <pre>
 *   "'lock:like:' + #postId"   → "lock:like:42"
 *   "'lock:follow:' + #memberId" → "lock:follow:7"
 * </pre>
 *
 * <p><b>waitTime — 락 획득 대기 시간</b><br>
 * {@code 0} (기본값): 락이 없으면 즉시 실패 → 빠른 응답 (버튼 중복 클릭 방지)<br>
 * {@code 양수}: 지정 시간 동안 재시도 → 처리량 우선
 *
 * <p><b>leaseTime — 락 자동 만료 시간</b><br>
 * {@code -1} (기본값): watchdog 활성화 → 비즈니스 로직이 길어져도 락이 자동 갱신됨<br>
 * {@code 양수}: 지정 시간 후 강제 만료 (watchdog 비활성화)
 */
@Target(ElementType.METHOD)       // 메서드에만 붙일 수 있다
@Retention(RetentionPolicy.RUNTIME) // 런타임에 AOP가 읽을 수 있어야 한다
public @interface DistributedLock {

    /**
     * 락 키 — SpEL 표현식으로 동적 키를 지원한다.
     * 예: {@code "'lock:like:' + #postId"}
     */
    String key();

    /**
     * 락 획득 대기 시간. 기본값 {@code 0}은 즉시 실패(fail-fast).
     */
    long waitTime() default 0;

    /**
     * 락 자동 만료 시간. 기본값 {@code -1}은 Redisson watchdog 활성화.
     */
    long leaseTime() default -1;

    /**
     * 시간 단위. 기본값 {@code TimeUnit.SECONDS}.
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
