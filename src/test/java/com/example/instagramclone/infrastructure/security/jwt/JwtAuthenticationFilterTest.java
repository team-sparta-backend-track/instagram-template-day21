package com.example.instagramclone.infrastructure.security.jwt;

import com.example.instagramclone.core.constant.AuthConstants;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.example.instagramclone.domain.member.domain.MemberRole;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * JwtAuthenticationFilter 단위 테스트
 *
 * [핵심 설계 검증]
 * validateToken()은 예외를 throw하는 구조이고, 필터가 각 예외 유형을 catch하여 처리합니다.
 * - 유효: SecurityContext에 인증 저장 → 다음 필터로 이동
 * - 만료(ExpiredJwtException): 인증 미저장 → 다음 필터로 이동 (AuthenticationEntryPoint가 401 처리)
 * - 변조(SignatureException): 동일
 * - 토큰 없음: 동일
 * - Bearer 접두사 없음: 동일
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private FilterChain filterChain;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ============================================================
    // 정상 동작
    // ============================================================

    @Nested
    @DisplayName("유효한 토큰")
    class ValidToken {

        @Test
        @DisplayName("유효한 JWT 토큰이면 SecurityContext에 인증 정보가 저장되고 다음 필터로 이동한다")
        void valid_token_sets_authentication() throws ServletException, IOException {
            String validToken = "valid.jwt.token";
            Long expectedMemberId = 1L;
            String expectedRole = MemberRole.USER.getKey();

            request.addHeader(AuthConstants.AUTHORIZATION_HEADER, AuthConstants.BEARER_PREFIX + validToken);
            given(jwtTokenProvider.validateToken(validToken)).willReturn(true);
            given(jwtTokenProvider.getMemberId(validToken)).willReturn(expectedMemberId);
            given(jwtTokenProvider.getRole(validToken)).willReturn(expectedRole);

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getAuthorities())
                    .extracting("authority")
                    .containsExactly(expectedRole);

            then(filterChain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("유효한 토큰의 Principal은 LoginUserInfoDto이고 memberId를 담는다")
        void valid_token_principal_contains_memberId() throws ServletException, IOException {
            String validToken = "valid.jwt.token";

            request.addHeader(AuthConstants.AUTHORIZATION_HEADER, AuthConstants.BEARER_PREFIX + validToken);
            given(jwtTokenProvider.validateToken(validToken)).willReturn(true);
            given(jwtTokenProvider.getMemberId(validToken)).willReturn(42L);
            given(jwtTokenProvider.getRole(validToken)).willReturn("ROLE_USER");

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            // Principal은 LoginUserInfoDto이고, id 필드에 42L이 담겨있어야 함
            assertThat(auth.getPrincipal()).hasFieldOrPropertyWithValue("id", 42L);
        }
    }

    // ============================================================
    // 토큰 없음 / 형식 오류
    // ============================================================

    @Nested
    @DisplayName("토큰 없음 / 형식 오류")
    class MissingOrMalformedHeader {

        @Test
        @DisplayName("Authorization 헤더가 없으면 인증 정보 없이 다음 필터로 이동한다")
        void no_header_passes_through_without_authentication() throws ServletException, IOException {
            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            then(filterChain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("'Bearer ' 접두사가 없는 잘못된 형식의 헤더는 토큰 파싱 없이 다음 필터로 이동한다")
        void invalid_prefix_passes_through_without_authentication() throws ServletException, IOException {
            request.addHeader(AuthConstants.AUTHORIZATION_HEADER, "Basic some.base64.encoded.string=");

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            then(filterChain).should().doFilter(request, response);
        }
    }

    // ============================================================
    // [핵심] 예외 throw 시나리오 - 필터의 catch 분기 검증
    // ============================================================

    @Nested
    @DisplayName("validateToken() 예외 throw 시나리오")
    class TokenExceptions {

        @Test
        @DisplayName("만료된 토큰(ExpiredJwtException) - 인증 미저장, 다음 필터로 이동, request에 예외 속성 설정")
        void expired_token_is_caught_and_continues_filter_chain() throws ServletException, IOException {
            String expiredToken = "expired.jwt.token";
            request.addHeader(AuthConstants.AUTHORIZATION_HEADER, AuthConstants.BEARER_PREFIX + expiredToken);
            given(jwtTokenProvider.validateToken(expiredToken))
                    .willThrow(new ExpiredJwtException(null, null, "토큰이 만료되었습니다."));

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            // 인증 정보가 설정되지 않아야 함
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            // 예외 정보가 request attribute에 저장되어야 함 (EntryPoint로 전달)
            assertThat(request.getAttribute("exception")).isInstanceOf(ExpiredJwtException.class);
            // 필터 체인은 계속 진행되어야 함
            then(filterChain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("서명이 변조된 토큰(SignatureException) - 인증 미저장, 다음 필터로 이동")
        void tampered_signature_is_caught_and_continues_filter_chain() throws ServletException, IOException {
            String tamperedToken = "tampered.jwt.token";
            request.addHeader(AuthConstants.AUTHORIZATION_HEADER, AuthConstants.BEARER_PREFIX + tamperedToken);
            given(jwtTokenProvider.validateToken(tamperedToken))
                    .willThrow(new SignatureException("잘못된 JWT 서명입니다."));

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(request.getAttribute("exception")).isInstanceOf(SignatureException.class);
            then(filterChain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("형식이 잘못된 토큰(MalformedJwtException) - 인증 미저장, 다음 필터로 이동")
        void malformed_token_is_caught_and_continues_filter_chain() throws ServletException, IOException {
            String malformedToken = "not.a.valid.jwt.at.all";
            request.addHeader(AuthConstants.AUTHORIZATION_HEADER, AuthConstants.BEARER_PREFIX + malformedToken);
            given(jwtTokenProvider.validateToken(malformedToken))
                    .willThrow(new MalformedJwtException("잘못된 JWT 형식입니다."));

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(request.getAttribute("exception")).isInstanceOf(MalformedJwtException.class);
            then(filterChain).should().doFilter(request, response);
        }

        @Test
        @DisplayName("어떤 예외가 발생하든 필터 체인은 반드시 다음으로 이동한다 (서비스 무중단 보장)")
        void any_exception_always_continues_filter_chain() throws ServletException, IOException {
            String badToken = "some.bad.token";
            request.addHeader(AuthConstants.AUTHORIZATION_HEADER, AuthConstants.BEARER_PREFIX + badToken);
            given(jwtTokenProvider.validateToken(badToken))
                    .willThrow(new RuntimeException("알 수 없는 JWT 오류"));

            jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            then(filterChain).should().doFilter(request, response);
        }
    }
}
