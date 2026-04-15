package com.example.instagramclone.core.config;

import com.example.instagramclone.core.constant.CacheNames;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Spring Cache + Redis 설정.
 *
 * <p><b>왜 이 클래스가 필요한가?</b><br>
 * {@code spring-boot-starter-data-redis}가 classpath에 있고 {@code @EnableCaching}이 붙어 있으면
 * Spring Boot가 {@link RedisCacheManager}를 자동 구성해 준다.
 * 단, 기본값은 TTL=무한 · 직렬화=JDK 바이트스트림 이다.
 * <ul>
 *   <li>TTL을 주지 않으면 캐시 데이터가 Redis에 영원히 남아 "썩은 데이터" 문제를 유발한다.</li>
 *   <li>JDK 직렬화는 {@link java.io.Serializable} 구현 강제 + 가독성 없음 → Jackson JSON 직렬화로 대체한다.</li>
 * </ul>
 * 따라서 커스텀 {@link RedisCacheManager} 빈을 직접 등록한다.</p>
 *
 * <p><b>직렬화 전략</b><br>
 * 키: {@link StringRedisSerializer} — redis-cli 에서 문자열로 바로 읽힌다.<br>
 * 값: {@link GenericJackson2JsonRedisSerializer} — JSON 형태로 저장, Record/POJO 모두 지원.</p>
 *
 * <pre>
 * redis-cli 에서 확인 예시:
 *   SCAN 0 MATCH "profileStats*"  → "profileStats::42" 형태의 키 목록
 *   GET "profileStats::42"        → JSON 형태의 MemberProfileResponse 가 보인다.
 * </pre>
 */
@Configuration
@EnableCaching   // Spring Cache 추상화 활성화 — @Cacheable / @CacheEvict 등 AOP 프록시가 작동하기 시작한다.
public class CacheConfig {

    /**
     * 커스텀 {@link RedisCacheManager} 빈.
     *
     * <p>Spring Boot 자동 구성 대신 이 빈이 등록되면 자동 구성은 적용되지 않는다.
     * TTL·직렬화·캐시별 설정을 여기서 한 곳에서 관리한다.</p>
     *
     * @param connectionFactory Spring Boot 자동 구성 RedisConnectionFactory (Lettuce)
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, MeterRegistry meterRegistry) {

        // 값 직렬화: Jackson JSON
        // GenericJackson2JsonRedisSerializer 는 @class 타입 정보를 JSON 안에 포함해
        // 역직렬화 시 어떤 타입으로 복원할지 자동으로 안다.
        // → redis-cli GET 하면 사람이 읽을 수 있는 JSON 이 보인다.
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        // 모든 캐시에 적용되는 기본 설정
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                // TTL: 5분 — 지나면 Redis 가 자동 삭제한다.
                // TTL 없이 두면 썩은 데이터가 영원히 남는다. 항상 명시할 것!
                .entryTtl(Duration.ofMinutes(5))
                // 키: 문자열 → redis-cli 에서 "profileStats::42" 처럼 사람이 읽을 수 있다.
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                // 값: Jackson JSON → JDK 바이트스트림 대신 JSON 으로 저장
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));
                // null 캐싱: 기본값 true → Cache Penetration 방어
                // 존재하지 않는 키 반복 조회 시 매번 DB 를 찌르는 것을 막는다.
                // 단, 실제 데이터가 생겼을 때 @CacheEvict 를 빠뜨리면 영원히 null 로 보이니 주의.
                // .disableCachingNullValues()  ← null 캐싱을 끄고 싶을 때만 추가

        // 캐시별 개별 TTL
        Map<String, RedisCacheConfiguration> perCacheConfig = Map.of(
                // 프로필 헤더 집계: 5분 TTL
                // 팔로워/게시물 수 — 팔로우·게시물 이벤트 시 Evict 와 조합
                CacheNames.PROFILE_STATS, defaultConfig.entryTtl(Duration.ofMinutes(5)),

                // 프로필 그리드 목록: 2분 TTL (헤더보다 짧게)
                // 게시물이 추가되면 0페이지부터 모든 페이지가 밀리므로 변화가 더 잦다.
                // Evict 가 allEntries=true 로 터지더라도 TTL 이 짧으면 자연 만료로도 보완된다.
                CacheNames.PROFILE_GRID, defaultConfig.entryTtl(Duration.ofMinutes(2))
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCacheConfig)
                .build();
    }
}
