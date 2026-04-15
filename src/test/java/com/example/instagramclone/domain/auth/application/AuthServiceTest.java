package com.example.instagramclone.domain.auth.application;

import com.example.instagramclone.core.exception.MemberErrorCode;
import com.example.instagramclone.core.exception.MemberException;
import com.example.instagramclone.domain.auth.api.AuthTokens;
import com.example.instagramclone.domain.auth.api.LoginRequest;
import com.example.instagramclone.domain.auth.api.LoginResponse;
import com.example.instagramclone.domain.auth.domain.RefreshToken;
import com.example.instagramclone.domain.auth.domain.RefreshTokenRepository;
import com.example.instagramclone.domain.member.application.MemberService;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.infrastructure.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * AuthService 단위 테스트
 *
 * [Mock 구조 설계 의도]
 * AuthService는 MemberRepository를 직접 의존하지 않습니다.
 * 회원 조회는 반드시 MemberService API를 통해서만 이루어지므로,
 * MemberRepository가 아닌 MemberService를 Mock합니다.
 * 이 구조를 어기면 테스트가 실제 코드와 단절(False Positive)됩니다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MemberService memberService; // AuthService의 실제 의존성

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private com.example.instagramclone.domain.member.infrastructure.MemberMapper memberMapper;

    @InjectMocks
    private AuthService authService;

    // ============================================================
    // 테스트 픽스처 (Helper)
    // ============================================================

    private Member buildMockMember(Long id, String username) {
        Member member = Member.builder()
                .username(username)
                .password("encoded_password")
                .email(username + "@test.com")
                .name("테스트 유저")
                .build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    // ============================================================
    // login()
    // ============================================================

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("실패 - 존재하지 않는 회원 (MemberService가 예외를 던짐)")
        void login_fail_user_not_found() {
            // given
            LoginRequest request = new LoginRequest("not_found_user", "password123!");
            given(memberService.findByLoginId(request.username()))
                    .willThrow(new MemberException(MemberErrorCode.INVALID_CREDENTIALS));

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.INVALID_CREDENTIALS.getMessage());
        }

        @Test
        @DisplayName("실패 - 비밀번호 불일치 시 없는 회원과 동일한 예외 반환 (보안)")
        void login_fail_invalid_password() {
            // given
            LoginRequest request = new LoginRequest("test_user", "wrong_password");
            Member mockMember = buildMockMember(1L, "test_user");

            given(memberService.findByLoginId(request.username())).willReturn(mockMember);
            given(passwordEncoder.matches(eq(request.password()), eq(mockMember.getPassword())))
                    .willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.INVALID_CREDENTIALS.getMessage());
        }

        @Test
        @DisplayName("성공 - 최초 로그인, RefreshToken 신규 저장")
        void login_success_first_time() {
            // given
            LoginRequest request = new LoginRequest("test_user", "correct_password");
            Member mockMember = buildMockMember(1L, "test_user");

            given(memberService.findByLoginId(request.username())).willReturn(mockMember);
            given(passwordEncoder.matches(eq(request.password()), eq(mockMember.getPassword()))).willReturn(true);
            given(refreshTokenRepository.findByMemberId(1L)).willReturn(Optional.empty()); // 최초 로그인
            given(jwtTokenProvider.createAccessToken(eq(1L), anyString())).willReturn("mock.access.token");
            given(jwtTokenProvider.createRefreshToken(eq(1L))).willReturn("mock.refresh.token");

            AuthTokens mockTokens = new AuthTokens("mock.access.token", "mock.refresh.token");
            LoginResponse mockResponse = new LoginResponse(
                    mockTokens,
                    new LoginResponse.UserInfoDto(1L, "test_user", "테스트 유저", null)
            );
            given(memberMapper.toLoginResponse(any(Member.class), any(AuthTokens.class))).willReturn(mockResponse);

            // when
            LoginResponse response = authService.login(request);

            // then - 응답값 검증
            assertThat(response).isNotNull();
            assertThat(response.tokens().accessToken()).isEqualTo("mock.access.token");
            assertThat(response.tokens().refreshToken()).isEqualTo("mock.refresh.token");
            assertThat(response.user().id()).isEqualTo(1L);
            assertThat(response.user().username()).isEqualTo("test_user");

            // 행동 검증: 새 RefreshToken이 DB에 저장되었는지
            then(refreshTokenRepository).should().save(any(RefreshToken.class));
        }

        @Test
        @DisplayName("성공 - 재로그인 시 기존 RefreshToken을 새 토큰으로 업데이트 (RTR 핵심)")
        void login_success_updates_existing_refreshToken() {
            // given: 이미 로그인 이력이 있는 회원
            LoginRequest request = new LoginRequest("test_user", "correct_password");
            Member mockMember = buildMockMember(1L, "test_user");

            String oldRefreshToken = "old.refresh.token";
            RefreshToken existingRefreshToken = RefreshToken.builder()
                    .memberId(1L)
                    .token(oldRefreshToken)
                    .build();

            given(memberService.findByLoginId(request.username())).willReturn(mockMember);
            given(passwordEncoder.matches(eq(request.password()), eq(mockMember.getPassword()))).willReturn(true);
            given(refreshTokenRepository.findByMemberId(1L)).willReturn(Optional.of(existingRefreshToken)); // 기존 토큰 존재
            given(jwtTokenProvider.createAccessToken(eq(1L), anyString())).willReturn("new.access.token");
            given(jwtTokenProvider.createRefreshToken(eq(1L))).willReturn("new.refresh.token");
            given(memberMapper.toLoginResponse(any(Member.class), any(AuthTokens.class))).willReturn(
                    new LoginResponse(new AuthTokens("new.access.token", "new.refresh.token"),
                            new LoginResponse.UserInfoDto(1L, "test_user", "테스트 유저", null)));

            // when
            authService.login(request);

            // then - RTR: 기존 엔티티의 토큰이 새 값으로 업데이트되어야 하며, save()는 호출하면 안 됨
            assertThat(existingRefreshToken.getToken()).isEqualTo("new.refresh.token");
            then(refreshTokenRepository).should(never()).save(any());
        }
    }

    // ============================================================
    // reissue()
    // ============================================================

    @Nested
    @DisplayName("reissue()")
    class Reissue {

        @Test
        @DisplayName("성공 - RTR 정책에 따라 DB 토큰 업데이트")
        void reissue_success() {
            // given
            String oldRefreshToken = "old.refresh.token";
            String newRefreshToken = "new.refresh.token";
            Member mockMember = buildMockMember(1L, "test_user");

            RefreshToken mockRefreshTokenEntity = RefreshToken.builder()
                    .memberId(1L)
                    .token(oldRefreshToken)
                    .build();

            given(jwtTokenProvider.validateToken(oldRefreshToken)).willReturn(true);
            given(refreshTokenRepository.findByToken(oldRefreshToken)).willReturn(Optional.of(mockRefreshTokenEntity));
            given(memberService.findById(1L)).willReturn(mockMember);
            given(jwtTokenProvider.createAccessToken(eq(1L), anyString())).willReturn("new.access.token");
            given(jwtTokenProvider.createRefreshToken(eq(1L))).willReturn(newRefreshToken);

            // when
            AuthTokens result = authService.reissue(oldRefreshToken);

            // then
            assertThat(result.accessToken()).isEqualTo("new.access.token");
            assertThat(result.refreshToken()).isEqualTo(newRefreshToken);
            // DB 엔티티가 새 토큰으로 교체되었는지 확인 (핵심 RTR 검증)
            assertThat(mockRefreshTokenEntity.getToken()).isEqualTo(newRefreshToken);
        }

        @Test
        @DisplayName("실패 - JWT 자체가 유효하지 않은 토큰")
        void reissue_fail_invalid_token() {
            // given
            String invalidToken = "invalid.token";
            given(jwtTokenProvider.validateToken(invalidToken)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.reissue(invalidToken))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.UNAUTHORIZED_ACCESS.getMessage());
        }

        @Test
        @DisplayName("실패 - JWT는 유효하지만 DB에 존재하지 않는 토큰 (탈취 의심)")
        void reissue_fail_token_not_found_in_db() {
            // given: 서명은 유효하지만 DB에는 없는 토큰 → 탈취 혹은 로그아웃된 토큰
            String validButStolenToken = "valid.but.stolen.token";
            given(jwtTokenProvider.validateToken(validButStolenToken)).willReturn(true);
            given(refreshTokenRepository.findByToken(validButStolenToken)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.reissue(validButStolenToken))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.UNAUTHORIZED_ACCESS.getMessage());
        }

        @Test
        @DisplayName("실패 - RefreshToken은 DB에 있지만 해당 회원이 존재하지 않음 (데이터 정합성 오류)")
        void reissue_fail_member_not_found() {
            // given: 이론상 발생하기 어렵지만, 회원 탈퇴 후 토큰이 남아있는 경계값 케이스
            String orphanToken = "orphan.refresh.token";
            RefreshToken orphanEntity = RefreshToken.builder()
                    .memberId(999L)
                    .token(orphanToken)
                    .build();

            given(jwtTokenProvider.validateToken(orphanToken)).willReturn(true);
            given(refreshTokenRepository.findByToken(orphanToken)).willReturn(Optional.of(orphanEntity));
            given(memberService.findById(999L))
                    .willThrow(new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

            // when & then
            assertThatThrownBy(() -> authService.reissue(orphanToken))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
        }
    }

    // ============================================================
    // logout()
    // ============================================================

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("성공 - DB에서 RefreshToken 삭제")
        void logout_success() {
            // given
            String refreshToken = "mock.refresh.token";
            RefreshToken mockRefreshTokenEntity = RefreshToken.builder()
                    .memberId(1L)
                    .token(refreshToken)
                    .build();

            given(refreshTokenRepository.findByToken(refreshToken)).willReturn(Optional.of(mockRefreshTokenEntity));

            // when
            authService.logout(refreshToken);

            // then
            then(refreshTokenRepository).should().delete(mockRefreshTokenEntity);
        }

        @Test
        @DisplayName("성공 - null 토큰으로 로그아웃 시 DB 조회 없이 안전하게 종료 (쿠키 없는 클라이언트 방어)")
        void logout_with_null_token_does_nothing() {
            // when
            authService.logout(null);

            // then - RefreshTokenRepository는 절대 호출되면 안 됨
            then(refreshTokenRepository).should(never()).findByToken(anyString());
            then(refreshTokenRepository).should(never()).delete(any());
        }

        @Test
        @DisplayName("엣지케이스 - DB에 없는 토큰으로 로그아웃해도 예외 없이 종료")
        void logout_token_not_in_db_does_nothing() {
            // given: 이미 만료/삭제된 토큰으로 재로그아웃 시도
            String alreadyRemovedToken = "already.removed.token";
            given(refreshTokenRepository.findByToken(alreadyRemovedToken)).willReturn(Optional.empty());

            // when - 예외가 발생하지 않아야 함
            authService.logout(alreadyRemovedToken);

            // then
            then(refreshTokenRepository).should(never()).delete(any());
        }
    }
}
