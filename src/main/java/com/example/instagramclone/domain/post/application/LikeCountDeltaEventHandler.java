package com.example.instagramclone.domain.post.application;

import com.example.instagramclone.core.constant.RedisKeys;
import com.example.instagramclone.domain.post.event.LikeCountDeltaEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 트랜잭션 커밋 후에만 Redis INCR 실행.
 * → 트랜잭션이 롤백되면 Redis도 건드리지 않음!
 *
 * <p>{@code @Async}는 붙이지 않음 — Redis INCR은 1ms 이내로 충분히 빠르고,
 * 순서 보장이 중요하므로 동기 처리가 안전합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LikeCountDeltaEventHandler {

    private final StringRedisTemplate redisTemplate;

    @TransactionalEventListener
    public void handleLikeCountDelta(LikeCountDeltaEvent event) {
        String key = RedisKeys.LIKE_DELTA_PREFIX + event.postId();
        redisTemplate.opsForValue().increment(key, event.delta());

        log.debug("[Write-Back] postId={}, delta={}", event.postId(), event.delta());
    }
}
