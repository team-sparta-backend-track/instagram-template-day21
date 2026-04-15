package com.example.instagramclone.domain.post.application;

import com.example.instagramclone.domain.post.event.LikeCountDeltaEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * LikeCountDeltaEventHandler 단위 테스트.
 *
 * [테스트 범위]
 * - +1 delta → Redis INCR +1 호출
 * - -1 delta → Redis INCR -1 호출
 * - 올바른 Redis 키 형식 사용
 */
@ExtendWith(MockitoExtension.class)
class LikeCountDeltaEventHandlerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private LikeCountDeltaEventHandler handler;

    @Test
    @DisplayName("좋아요 이벤트(delta=+1) → Redis INCR +1 호출")
    void like_increments_redis() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        LikeCountDeltaEvent event = new LikeCountDeltaEvent(42L, +1);

        handler.handleLikeCountDelta(event);

        then(valueOperations).should().increment("like:delta:42", 1);
    }

    @Test
    @DisplayName("좋아요 취소 이벤트(delta=-1) → Redis INCR -1 호출")
    void unlike_decrements_redis() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        LikeCountDeltaEvent event = new LikeCountDeltaEvent(100L, -1);

        handler.handleLikeCountDelta(event);

        then(valueOperations).should().increment("like:delta:100", -1);
    }
}
