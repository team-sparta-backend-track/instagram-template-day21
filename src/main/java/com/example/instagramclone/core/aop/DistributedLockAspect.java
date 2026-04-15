package com.example.instagramclone.core.aop;

import com.example.instagramclone.core.aop.annotation.DistributedLock;
import com.example.instagramclone.core.exception.LockAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * [Day 18 과제] {@link DistributedLock} 어노테이션을 처리하는 AOP Aspect.
 *
 * <p><b>핵심 실행 순서</b>
 * <pre>
 *   DistributedLockAspect (락 획득)
 *     └─ TransactionAspect (@Transactional)
 *           └─ 실제 메서드 실행
 *         └─ 트랜잭션 커밋
 *     └─ 락 해제  ← 커밋 이후!
 * </pre>
 *
 * <p><b>왜 @Order(1)인가? — @Transactional보다 먼저 감싸야 한다</b><br>
 * Spring AOP에서 {@code @Order} 값이 작을수록 더 바깥쪽 Aspect가 된다.
 * {@code @Transactional}의 기본 Order는 {@link Integer#MAX_VALUE} — 가장 안쪽.
 * 이 Aspect를 {@code @Order(1)}로 설정하면 아래 구조가 만들어진다.
 * <pre>
 *   [락 획득] → [트랜잭션 시작] → [비즈니스 로직] → [트랜잭션 커밋] → [락 해제]
 * </pre>
 * 반대로 이 Aspect의 Order를 @Transactional보다 크게 하면:
 * <pre>
 *   [트랜잭션 시작] → [락 획득] → [비즈니스 로직] → [락 해제] → [트랜잭션 커밋]
 *                                                         ↑
 *                                          커밋 전에 락이 풀린다!
 *                                          다른 요청이 아직 커밋 안 된 데이터를 읽을 수 있다.
 * </pre>
 *
 * <p><b>SpEL 키 파싱 흐름</b>
 * <pre>
 *   어노테이션: key = "'lock:like:' + #postId"
 *   파라미터:   postId = 42
 *
 *   1. MethodSignature로 파라미터 이름 배열 추출  → ["loginMemberId", "postId"]
 *   2. joinPoint.getArgs()로 실제 값 배열 추출    → [1L, 42L]
 *   3. StandardEvaluationContext에 name=value 등록 → {postId=42}
 *   4. SpelExpressionParser로 표현식 평가         → "lock:like:42"
 * </pre>
 */
@Slf4j
@Aspect
@Order(1) // @Transactional(기본 Integer.MAX_VALUE)보다 Order가 낮아야 더 바깥쪽 Aspect가 된다
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;

    // SpelExpressionParser는 스레드 안전하므로 인스턴스 하나를 재사용한다
    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * {@link DistributedLock}이 붙은 메서드를 가로채 락을 획득·해제한다.
     */
    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock)
            throws Throwable {

        // ── 1. SpEL로 락 키 계산 ──────────────────────────────────────────────
        String lockKey = resolveKey(joinPoint, distributedLock.key());
        log.debug("[DistributedLock] 락 시도: key={}", lockKey);

        // ── 2. RLock 객체 획득 ────────────────────────────────────────────────
        // getLock()은 Redis에 접근하지 않는다. 키 이름과 연결된 락 객체를 반환할 뿐이다.
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;

        try {
            // ── 3. 락 획득 시도 ───────────────────────────────────────────────
            // tryLock(waitTime, leaseTime, timeUnit)
            // waitTime=0(기본값): 즉시 실패 → 빠른 응답
            // leaseTime=-1(기본값): watchdog 활성화 → 긴 작업에도 락 자동 갱신
            acquired = lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!acquired) {
                throw new LockAcquisitionException(
                        "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요. (key=" + lockKey + ")"
                );
            }

            log.debug("[DistributedLock] 락 획득: key={}", lockKey);

            // ── 4. 실제 메서드 실행 ───────────────────────────────────────────
            // proceed()가 반환될 때 @Transactional 커밋도 이미 완료된 상태다.
            // → 락 해제(finally)는 반드시 커밋 이후에 실행된다.
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            // tryLock 대기 중 스레드 인터럽트 발생
            Thread.currentThread().interrupt(); // 인터럽트 상태 복원
            throw new LockAcquisitionException("락 대기 중 인터럽트가 발생했습니다.", e);

        } finally {
            // ── 5. 락 해제 ────────────────────────────────────────────────────
            // acquired=true이고 현재 스레드가 락 보유자인 경우에만 해제한다.
            // isHeldByCurrentThread(): 다른 스레드의 락을 실수로 해제하는 사고를 막는다.
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("[DistributedLock] 락 해제: key={}", lockKey);
            }
        }
    }

    /**
     * SpEL 표현식을 평가해 실제 락 키 문자열을 반환한다.
     *
     * <pre>
     *   key = "'lock:like:' + #postId", postId=42  →  "lock:like:42"
     * </pre>
     *
     * @param joinPoint      현재 실행 중인 메서드 정보 (파라미터 이름·값)
     * @param keyExpression  {@link DistributedLock#key()}의 SpEL 표현식 문자열
     * @return 평가된 락 키 문자열
     */
    private String resolveKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        // 메서드 시그니처에서 파라미터 이름 배열을 가져온다
        // 예: toggleLike(Long loginMemberId, Long postId) → ["loginMemberId", "postId"]
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();

        // 실제 호출 시 전달된 인수 값 배열
        // 예: toggleLike(1L, 42L) → [1L, 42L]
        Object[] args = joinPoint.getArgs();

        // SpEL 컨텍스트에 파라미터 이름=값을 등록한다
        // → 표현식 안에서 #postId, #loginMemberId 등으로 참조 가능해진다
        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        // SpEL 표현식 파싱 및 평가
        // 예: "'lock:like:' + #postId" → "lock:like:42"
        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }
}
