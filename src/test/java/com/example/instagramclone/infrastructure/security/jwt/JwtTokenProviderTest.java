package com.example.instagramclone.infrastructure.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtTokenProvider 단위 테스트
 *
 * [테스트 범위]
 * - createAccessToken / createRefreshToken: 발급 및 claim 파싱
 * - getMemberId / getRole: claim 추출 정확성
 * - validateToken: 정상/만료/변조 토큰 동작
 * - init(): 32바이트 미만 키 방어
 * - getRefreshTokenValidityInSeconds(): ms → s 변환
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();

        String plainSecret = "ThisIsASuperSecretKeyForJwtTokenGeneration"; // 42자 > 32바이트
        String base64Secret = Base64.getEncoder().encodeToString(plainSecret.getBytes());

        ReflectionTestUtils.setField(jwtTokenProvider, "secretKeyString", base64Secret);
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenValidityInMilliseconds", 3600000L);   // 1시간
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenValidityInMilliseconds", 864000000L); // 10일

        jwtTokenProvider.init();
    }

    // ============================================================
    // createAccessToken()
    // ============================================================

    @Nested
    @DisplayName("createAccessToken()")
    class CreateAccessToken {

        @Test
        @DisplayName("Access Token 발급 후 getMemberId()로 memberId 추출 가능")
        void createAndParseMemberId() {
            String token = jwtTokenProvider.createAccessToken(1L, "ROLE_USER");

            assertThat(token).isNotBlank();
            assertThat(jwtTokenProvider.getMemberId(token)).isEqualTo(1L);
        }

        @Test
        @DisplayName("Access Token에는 role claim이 포함된다")
        void accessToken_contains_role_claim() {
            String token = jwtTokenProvider.createAccessToken(1L, "ROLE_USER");

            assertThat(jwtTokenProvider.getRole(token)).isEqualTo("ROLE_USER");
        }

        @Test
        @DisplayName("발급된 Access Token은 validateToken() 검증을 통과한다")
        void accessToken_is_valid() {
            String token = jwtTokenProvider.createAccessToken(1L, "ROLE_USER");

            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("서로 다른 memberId로 발급한 토큰은 각자의 ID를 정확히 담는다")
        void different_memberIds_are_correctly_embedded() {
            String tokenA = jwtTokenProvider.createAccessToken(1L, "ROLE_USER");
            String tokenB = jwtTokenProvider.createAccessToken(999L, "ROLE_ADMIN");

            assertThat(jwtTokenProvider.getMemberId(tokenA)).isEqualTo(1L);
            assertThat(jwtTokenProvider.getMemberId(tokenB)).isEqualTo(999L);
        }
    }

    // ============================================================
    // createRefreshToken()
    // ============================================================

    @Nested
    @DisplayName("createRefreshToken()")
    class CreateRefreshToken {

        @Test
        @DisplayName("Refresh Token 발급 후 getMemberId()로 memberId 추출 가능")
        void createAndParseMemberId() {
            String token = jwtTokenProvider.createRefreshToken(2L);

            assertThat(token).isNotBlank();
            assertThat(jwtTokenProvider.getMemberId(token)).isEqualTo(2L);
        }

        @Test
        @DisplayName("Refresh Token에는 role claim이 없어 getRole()은 null 반환 (보안: 권한 정보 미포함)")
        void refreshToken_has_no_role_claim() {
            String token = jwtTokenProvider.createRefreshToken(2L);

            assertThat(jwtTokenProvider.getRole(token)).isNull();
        }

        @Test
        @DisplayName("발급된 Refresh Token은 validateToken() 검증을 통과한다")
        void refreshToken_is_valid() {
            String token = jwtTokenProvider.createRefreshToken(2L);

            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }
    }

    // ============================================================
    // validateToken()
    // ============================================================

    @Nested
    @DisplayName("validateToken()")
    class ValidateToken {

        @Test
        @DisplayName("유효한 토큰은 true 반환")
        void valid_token_returns_true() {
            String token = jwtTokenProvider.createAccessToken(1L, "ROLE_USER");

            assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("[CRITICAL] 만료된 토큰은 false 반환이 아닌 ExpiredJwtException을 throw한다 - 필터가 catch해서 처리하는 구조")
        void expired_token_throws_ExpiredJwtException() throws InterruptedException {
            // given: 유효기간 1ms인 토큰 생성 후 즉시 만료
            ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenValidityInMilliseconds", 1L);
            String expiredToken = jwtTokenProvider.createAccessToken(3L, "ROLE_USER");
            Thread.sleep(10);

            // when & then: false 반환이 아닌 예외를 throw해야 한다
            // JwtAuthenticationFilter의 catch (ExpiredJwtException e) 분기가 이를 처리한다
            assertThatThrownBy(() -> jwtTokenProvider.validateToken(expiredToken))
                    .isInstanceOf(ExpiredJwtException.class);
        }

        @Test
        @DisplayName("서명이 변조된 토큰은 예외를 throw한다 (위변조 방어)")
        void tampered_signature_throws_exception() {
            String validToken = jwtTokenProvider.createAccessToken(1L, "ROLE_USER");

            // JWT 구조: header.payload.signature — signature 부분을 변조
            String[] parts = validToken.split("\\.");
            String tamperedToken = parts[0] + "." + parts[1] + ".invalidsignature_tampered";

            assertThatThrownBy(() -> jwtTokenProvider.validateToken(tamperedToken))
                    .isInstanceOf(Exception.class); // SignatureException 또는 MalformedJwtException
        }
    }

    // ============================================================
    // init()
    // ============================================================

    @Nested
    @DisplayName("init()")
    class Init {

        @Test
        @DisplayName("32바이트 미만 키로 초기화 시 IllegalArgumentException 발생")
        void init_throws_for_key_shorter_than_32_bytes() {
            JwtTokenProvider shortKeyProvider = new JwtTokenProvider();

            // "short_key_under_32bytes" = 23자 → Base64 인코딩 후 디코딩해도 23바이트 < 32바이트
            String shortSecret = Base64.getEncoder().encodeToString("short_key_under_32bytes".getBytes());
            ReflectionTestUtils.setField(shortKeyProvider, "secretKeyString", shortSecret);
            ReflectionTestUtils.setField(shortKeyProvider, "accessTokenValidityInMilliseconds", 3600000L);
            ReflectionTestUtils.setField(shortKeyProvider, "refreshTokenValidityInMilliseconds", 864000000L);

            assertThatThrownBy(shortKeyProvider::init)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("32바이트");
        }

        @Test
        @DisplayName("정확히 32바이트 키로 초기화 시 성공 (경계값)")
        void init_succeeds_with_exactly_32_byte_key() {
            JwtTokenProvider exactProvider = new JwtTokenProvider();

            // "12345678901234567890123456789012" = 정확히 32자(32바이트)
            String exactSecret = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes());
            ReflectionTestUtils.setField(exactProvider, "secretKeyString", exactSecret);
            ReflectionTestUtils.setField(exactProvider, "accessTokenValidityInMilliseconds", 3600000L);
            ReflectionTestUtils.setField(exactProvider, "refreshTokenValidityInMilliseconds", 864000000L);

            // 예외 없이 초기화 성공해야 함
            exactProvider.init();
            String token = exactProvider.createAccessToken(1L, "ROLE_USER");
            assertThat(token).isNotBlank();
        }
    }

    // ============================================================
    // getRefreshTokenValidityInSeconds()
    // ============================================================

    @Nested
    @DisplayName("getRefreshTokenValidityInSeconds()")
    class GetRefreshTokenValidity {

        @Test
        @DisplayName("밀리초(864000000ms = 10일)를 초 단위(864000s)로 정확히 변환한다")
        void converts_milliseconds_to_seconds_correctly() {
            // setUp에서 refreshTokenValidityInMilliseconds = 864000000L (10일)
            int validityInSeconds = jwtTokenProvider.getRefreshTokenValidityInSeconds();

            assertThat(validityInSeconds).isEqualTo(864000); // 864000000 / 1000
        }
    }
}
