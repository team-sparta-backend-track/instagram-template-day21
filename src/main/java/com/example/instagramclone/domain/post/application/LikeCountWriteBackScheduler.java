package com.example.instagramclone.domain.post.application;

import com.example.instagramclone.core.constant.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * [Day 19 Step 4] Redis INCR Write-Back 스케줄러.
 *
 * <p>PostLikeService에서 좋아요 클릭 시 즉시 DB를 갱신하는 대신,
 * Redis에 delta(+1/-1)를 누적해 두고 이 스케줄러가 주기적으로 DB에 반영한다.
 *
 * <pre>
 * 좋아요 클릭 → @Transactional
 * ├── [즉시 DB] PostLike INSERT/DELETE  ← 원본 기록. 무결성 보장 필수
 * └── [Redis]  INCR like:delta:{postId} ← likeCount 갱신은 여기서 지연
 *
 * 스케줄러 (60초 주기)
 * ├── SCAN like:delta:*
 * ├── GETDEL like:delta:{postId}  ← 원자적으로 읽고 삭제
 * └── UPDATE posts SET like_count = like_count + ? WHERE id = ?
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeCountWriteBackScheduler {

    private final StringRedisTemplate redisTemplate;
    private final JdbcTemplate jdbcTemplate;

    /**
     * TODO Step 4: Redis에 누적된 delta를 DB에 반영한다.
     *
     * 힌트:
     *  1. redisTemplate.keys("like:delta:*") 로 키 목록 조회
     *  2. 각 키마다 redisTemplate.opsForValue().getAndDelete(key) — GETDEL (원자적)
     *  3. key에서 postId 파싱: key.replace("like:delta:", "")
     *  4. jdbcTemplate.batchUpdate(
     *         "UPDATE posts SET like_count = like_count + ? WHERE id = ?", params)
     *
     * ⚠️ 트랜잭션 경계 주의:
     *  @Transactional toggleLike() 안에서 Redis INCR 후 DB 롤백이 발생하면
     *  delta가 DB와 어긋날 수 있다. → Step 3 재계산 배치가 최후 안전망 역할을 한다.
     *  완전한 해결은 Day 21 @TransactionalEventListener(AFTER_COMMIT) 에서 다룬다.
     */

    @Scheduled(fixedDelay = 60_000)
    public void flushLikeCountDelta() {
        // SCAN: KEYS와 달리 커서 기반으로 조금씩 순회하므로 Redis를 블로킹하지 않는다.
        // KEYS는 O(N) 단일 명령이라 키가 수백만 개면 Redis가 수 초간 멈출 수 있다.
        ScanOptions options = ScanOptions.scanOptions()
                .match(RedisKeys.LIKE_DELTA_PATTERN)
                .count(100)     // 한 번의 SCAN 호출당 힌트 (보장값 아님)
                .build();

        List<Object[]> params = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                // GETDEL: 값을 읽는 동시에 삭제 (원자적, Redis 6.2+)
                String value = redisTemplate.opsForValue().getAndDelete(key);
                if (value == null) continue;
                long postId = RedisKeys.postIdFromLikeDelta(key);
                long delta  = Long.parseLong(value);
                params.add(new Object[]{delta, postId});
            }
        }

        if (!params.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "UPDATE posts SET like_count = GREATEST(0, like_count + ?) WHERE id = ?",
                    params
            );
            log.info("[Write-Back flush] {}건 반영", params.size());
        }
    }
}
