package com.example.instagramclone.domain.post.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * [Day 18 Step 3] Redis SET NX EX를 이용한 분산 락 직접 구현.
 *
 * <p><b>왜 분산 락이 필요한가?</b><br>
 * JPA {@code @Lock(PESSIMISTIC_WRITE)}는 DB 내부에서 행(row)을 잠근다.
 * 서버가 여러 대라도 DB는 하나이므로 기술적으로는 동작하지만,
 * 락 경합이 DB 부하로 직결되고, DB를 샤딩/분산하는 순간 재설계가 필요해진다.
 * Redis에 <b>글로벌 키</b> 하나를 두면 서버 대수와 무관하게 "한 번에 한 요청만" 처리할 수 있다.
 *
 * <p><b>핵심 원리 — SET NX EX</b><br>
 * <pre>
 *   SET lock:like:1  "uuid값"  NX  EX 3
 *   │                          │   └─ 3초 뒤 자동 만료 (프로세스가 죽어도 락이 해제됨)
 *   │                          └──── NX: 키가 없을 때만 SET → 락 획득
 *   └── 키가 이미 있으면 SET 실패 → 락 획득 실패, 다른 서버가 보유 중
 * </pre>
 *
 * <p><b>Lua 스크립트로 락 해제하는 이유</b><br>
 * GET(내 값인지 확인) → DEL(삭제)을 별개 명령어로 보내면,
 * GET과 DEL 사이에 락이 만료되어 다른 서버가 새 락을 가져갈 수 있다.
 * Lua 스크립트는 Redis에서 <b>원자적으로</b> 실행되므로 이 경쟁 조건을 막아 준다.
 *
 * <p><b>키 네이밍 관례 (prefix로 목적 구분)</b>
 * <pre>
 *   cache:profile:{id}   ← Day 17 캐시 키
 *   lock:like:{postId}   ← Day 18 락 키
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 락 획득 시도.
     *
     * <p>Redis에 {@code SET key value NX EX timeoutSeconds} 명령을 보낸다.
     * <ul>
     *   <li>키가 없으면 → 세팅 성공(락 획득) → {@code true} 반환</li>
     *   <li>키가 이미 있으면 → 세팅 생략(락 획득 실패) → {@code false} 반환</li>
     * </ul>
     *
     * @param key            락 키 (예: {@code "lock:like:1"})
     * @param value          락 소유자 식별값 — UUID를 사용해 서버마다 고유하게 만든다
     * @param timeoutSeconds 락 자동 만료 시간 (초). 처리 중 서버가 죽어도 이 시간 후 자동 해제
     * @return {@code true}: 락 획득 성공 / {@code false}: 이미 다른 요청이 락을 보유 중
     */
    public boolean tryLock(String key, String value, long timeoutSeconds) {
        // setIfAbsent = SET key value NX EX timeoutSeconds
        // → 키가 없을 때만(NX) 값을 세팅하고 만료 시간(EX)을 설정한다.
        // → 세팅 성공 시 Boolean.TRUE, 실패(이미 존재) 시 Boolean.FALSE 또는 null 반환
        // Boolean.TRUE.equals(...)로 null-safe 처리한다.
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue()
                        .setIfAbsent(key, value, timeoutSeconds, TimeUnit.SECONDS)
        );
    }

    /**
     * 락 해제 (Lua 스크립트로 원자적 처리).
     *
     * <p>본인이 건 락인지 {@code value}(UUID)를 먼저 확인한 후 삭제한다.
     * 락이 이미 만료되어 다른 서버가 새 락을 가져간 경우에는 삭제하지 않는다.
     *
     * <p><b>Lua 스크립트 동작 원리</b>
     * <pre>
     *   KEYS[1] = 락 키  (예: "lock:like:1")
     *   ARGV[1] = 락 값  (예: 내가 저장한 UUID)
     *
     *   if redis.call('get', KEYS[1]) == ARGV[1] then
     *       return redis.call('del', KEYS[1])  -- 내 락 맞음 → 삭제
     *   else
     *       return 0                            -- 남의 락 또는 이미 만료 → 건드리지 않음
     *   end
     * </pre>
     *
     * @param key   락 키
     * @param value 락 획득 시 저장했던 UUID (본인 확인용)
     */
    public void releaseLock(String key, String value) {
        // GET + DEL을 별개 명령어로 보내면 비원자적이다.
        // Lua 스크립트는 Redis 서버에서 단일 명령처럼 원자적으로 실행된다.
        // 스크립트 안의 KEYS[1]은 고정 문자열이 아니라, execute()에 넘긴 키 목록의 첫 번째 요소(= 아래 key)를 가리킨다.
        String script = """
                if redis.call('get', KEYS[1]) == ARGV[1] then 
                    return redis.call('del', KEYS[1]) 
                else
                    return 0
                end
                """;

        // 반환: del은 삭제된 키 개수(0 또는 1), else 분기는 0 → 모두 Redis 정수(integer) 응답.
        // Spring Data Redis는 이런 정수 결과를 Long으로 역직렬화하므로 resultType을 Long으로 둔다. (여기서는 반환값 미사용)
        redisTemplate.execute(
                new DefaultRedisScript<>(script, Long.class),
                List.of(key),   // KEYS[1]에 대응
                value           // ARGV[1]에 대응
        );
    }
}
