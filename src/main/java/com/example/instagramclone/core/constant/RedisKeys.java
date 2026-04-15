package com.example.instagramclone.core.constant;

/**
 * Redis 키 상수 모음.
 *
 * <p>키 문자열을 코드 곳곳에 리터럴로 흩어 두면 오타가 나도 컴파일 시점에 잡히지 않는다.
 * 이 클래스에 모아 두면 IDE 자동완성 + 오타 방지 + 전체 Redis 키 목록 파악이 한 곳에서 가능하다.
 *
 * <p>네이밍 규칙: 도메인_용도_PREFIX / 도메인_용도_PATTERN
 *
 * <pre>
 * 사용 예:
 *   redissonClient.getLock(RedisKeys.lockLike(postId))
 *   redisTemplate.keys(RedisKeys.LIKE_DELTA_PATTERN)
 *   redisTemplate.opsForValue().increment(RedisKeys.likeDelta(postId), +1)
 * </pre>
 *
 * <p>Day별 Redis 키 용도 요약:
 * <pre>
 *   Day 17 — Spring @Cacheable  : 키 관리는 CacheNames.java (Spring Cache 추상화)
 *   Day 18 — 분산 락            : lock:like:{postId}
 *   Day 19 — Write-Back delta   : like:delta:{postId}
 * </pre>
 */
public final class RedisKeys {

    // =========================================================================
    // Day 18: 분산 락 (Distributed Lock)
    // =========================================================================

    /** 좋아요 분산 락 키 접두사. 전체 키는 {@link #lockLike(long)} 사용. */
    public static final String LOCK_LIKE_PREFIX = "lock:like:";

    /**
     * 게시물 좋아요 분산 락 키를 생성한다.
     * <pre>lockLike(42) → "lock:like:42"</pre>
     */
    public static String lockLike(long postId) {
        return LOCK_LIKE_PREFIX + postId;
    }

    // =========================================================================
    // Day 19: Write-Back delta
    // =========================================================================

    /** 좋아요 delta 키 접두사. 전체 키는 {@link #likeDelta(long)} 사용. */
    public static final String LIKE_DELTA_PREFIX = "like:delta:";

    /** Write-Back 스케줄러에서 SCAN/KEYS 패턴으로 사용. */
    public static final String LIKE_DELTA_PATTERN = "like:delta:*";

    /**
     * 게시물 좋아요 delta 키를 생성한다.
     * <pre>likeDelta(42) → "like:delta:42"</pre>
     */
    public static String likeDelta(long postId) {
        return LIKE_DELTA_PREFIX + postId;
    }

    /**
     * delta 키에서 postId를 파싱한다.
     * <pre>postIdFromLikeDelta("like:delta:42") → 42L</pre>
     */
    public static long postIdFromLikeDelta(String key) {
        return Long.parseLong(key.substring(LIKE_DELTA_PREFIX.length()));
    }

    private RedisKeys() {
        // 유틸리티 클래스 — 인스턴스화 방지
    }
}
