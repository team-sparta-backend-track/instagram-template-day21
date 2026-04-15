package com.example.instagramclone.domain.member.application;

import com.example.instagramclone.core.exception.CommonErrorCode;
import com.example.instagramclone.core.exception.MemberErrorCode;
import com.example.instagramclone.core.exception.MemberException;
import com.example.instagramclone.domain.auth.api.SignUpRequest;
import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import com.example.instagramclone.domain.member.domain.MemberRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * MemberService 단위 테스트
 *
 * [테스트 범위]
 * - createMember(): 중복 검증 순서, 조기 종료, 필드 매핑, 비밀번호 암호화
 * - findByLoginId(): 이메일/전화번호/username 분기, 미존재 시 동일 예외(사용자 열거 방지)
 * - findById(): 정상 조회, 미존재 예외
 * - findByUsername(): username 기반 프로필 진입용 조회
 * - checkDuplicate(): 6가지 조합(3타입 × 존재/미존재), 잘못된 타입
 * - getReferenceById(): Repository 위임 검증
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

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
    // createMember()
    // ============================================================

    @Nested
    @DisplayName("createMember()")
    class CreateMember {

        @Test
        @DisplayName("실패 - 이메일 중복 시 예외 발생, 비밀번호 암호화 및 저장 미호출")
        void fail_duplicate_email() {
            SignUpRequest request = SignUpRequest.builder()
                    .username("new_user")
                    .password("password!23")
                    .emailOrPhone("test@test.com")
                    .name("New User")
                    .build();

            given(memberRepository.existsByEmail("test@test.com")).willReturn(true);

            assertThatThrownBy(() -> memberService.createMember(request))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.DUPLICATE_EMAIL.getMessage());

            then(passwordEncoder).shouldHaveNoInteractions();
            then(memberRepository).should(never()).save(any(Member.class));
        }

        @Test
        @DisplayName("실패 - 이메일 중복 시 username 중복 체크 없이 조기 종료 (불필요한 DB 조회 방지)")
        void fail_duplicate_email_skips_username_check() {
            SignUpRequest request = SignUpRequest.builder()
                    .username("some_user")
                    .password("password!23")
                    .emailOrPhone("dup@test.com")
                    .name("Some User")
                    .build();

            given(memberRepository.existsByEmail("dup@test.com")).willReturn(true);

            assertThatThrownBy(() -> memberService.createMember(request))
                    .isInstanceOf(MemberException.class);

            then(memberRepository).should(never()).existsByUsername(anyString());
        }

        @Test
        @DisplayName("실패 - 전화번호 중복 시 예외 발생, 비밀번호 암호화 및 저장 미호출")
        void fail_duplicate_phone() {
            SignUpRequest request = SignUpRequest.builder()
                    .username("new_user")
                    .password("password!23")
                    .emailOrPhone("01012345678")
                    .name("New User")
                    .build();

            given(memberRepository.existsByPhone("01012345678")).willReturn(true);

            assertThatThrownBy(() -> memberService.createMember(request))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.DUPLICATE_PHONE.getMessage());

            then(passwordEncoder).shouldHaveNoInteractions();
            then(memberRepository).should(never()).save(any(Member.class));
        }

        @Test
        @DisplayName("실패 - 전화번호 중복 시 username 중복 체크 없이 조기 종료 (불필요한 DB 조회 방지)")
        void fail_duplicate_phone_skips_username_check() {
            SignUpRequest request = SignUpRequest.builder()
                    .username("some_user")
                    .password("password!23")
                    .emailOrPhone("01099999999")
                    .name("Some User")
                    .build();

            given(memberRepository.existsByPhone("01099999999")).willReturn(true);

            assertThatThrownBy(() -> memberService.createMember(request))
                    .isInstanceOf(MemberException.class);

            then(memberRepository).should(never()).existsByUsername(anyString());
        }

        @Test
        @DisplayName("실패 - 유저네임 중복 시 예외 발생, 비밀번호 암호화 및 저장 미호출")
        void fail_duplicate_username() {
            SignUpRequest request = SignUpRequest.builder()
                    .username("existing_user")
                    .password("password!23")
                    .emailOrPhone("test@test.com")
                    .name("New User")
                    .build();

            given(memberRepository.existsByEmail("test@test.com")).willReturn(false);
            given(memberRepository.existsByUsername("existing_user")).willReturn(true);

            assertThatThrownBy(() -> memberService.createMember(request))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.DUPLICATE_USERNAME.getMessage());

            then(passwordEncoder).shouldHaveNoInteractions();
            then(memberRepository).should(never()).save(any(Member.class));
        }

        @Test
        @DisplayName("성공 - 이메일 가입: email 필드 설정, phone은 null, 비밀번호 암호화 후 저장")
        void success_with_email() {
            SignUpRequest request = SignUpRequest.builder()
                    .username("new_user")
                    .password("password!23")
                    .emailOrPhone("test@test.com")
                    .name("New User")
                    .build();

            given(memberRepository.existsByEmail("test@test.com")).willReturn(false);
            given(memberRepository.existsByUsername("new_user")).willReturn(false);
            given(passwordEncoder.encode("password!23")).willReturn("encoded_password!23");

            ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
            given(memberRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

            memberService.createMember(request);

            Member saved = captor.getValue();
            assertThat(saved.getEmail()).isEqualTo("test@test.com");
            assertThat(saved.getPhone()).isNull();
            assertThat(saved.getPassword()).isEqualTo("encoded_password!23");
            assertThat(saved.getUsername()).isEqualTo("new_user");
            assertThat(saved.getName()).isEqualTo("New User");
            assertThat(saved.getRole()).isEqualTo(MemberRole.USER);
        }

        @Test
        @DisplayName("성공 - 전화번호 가입: phone 필드 설정, email은 null, 비밀번호 암호화 후 저장")
        void success_with_phone() {
            SignUpRequest request = SignUpRequest.builder()
                    .username("new_user")
                    .password("password!23")
                    .emailOrPhone("01012345678")
                    .name("New User")
                    .build();

            given(memberRepository.existsByPhone("01012345678")).willReturn(false);
            given(memberRepository.existsByUsername("new_user")).willReturn(false);
            given(passwordEncoder.encode("password!23")).willReturn("encoded_phone!23");

            ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
            given(memberRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

            memberService.createMember(request);

            Member saved = captor.getValue();
            assertThat(saved.getPhone()).isEqualTo("01012345678");
            assertThat(saved.getEmail()).isNull();
            assertThat(saved.getPassword()).isEqualTo("encoded_phone!23");
            assertThat(saved.getRole()).isEqualTo(MemberRole.USER);
        }

        @Test
        @DisplayName("보안 - 평문 비밀번호가 아닌 암호화된 값이 저장되어야 한다")
        void password_must_be_encoded_not_plaintext() {
            String rawPassword = "rawPassword!1";
            SignUpRequest request = SignUpRequest.builder()
                    .username("new_user")
                    .password(rawPassword)
                    .emailOrPhone("user@test.com")
                    .name("Tester")
                    .build();

            given(memberRepository.existsByEmail("user@test.com")).willReturn(false);
            given(memberRepository.existsByUsername("new_user")).willReturn(false);
            given(passwordEncoder.encode(rawPassword)).willReturn("$2a$10$encoded_hash");

            ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
            given(memberRepository.save(captor.capture())).willAnswer(inv -> inv.getArgument(0));

            memberService.createMember(request);

            Member saved = captor.getValue();
            assertThat(saved.getPassword()).isNotEqualTo(rawPassword);
            assertThat(saved.getPassword()).isEqualTo("$2a$10$encoded_hash");
        }
    }

    // ============================================================
    // findByLoginId()
    // ============================================================

    @Nested
    @DisplayName("findByLoginId()")
    class FindByLoginId {

        @Test
        @DisplayName("성공 - '@' 포함 아이디는 이메일로 조회, 다른 조회 메서드 미호출")
        void success_email_login() {
            String email = "user@test.com";
            Member mockMember = buildMockMember(1L, "testuser");
            given(memberRepository.findByEmail(email)).willReturn(Optional.of(mockMember));

            Member result = memberService.findByLoginId(email);

            assertThat(result).isEqualTo(mockMember);
            then(memberRepository).should().findByEmail(email);
            then(memberRepository).should(never()).findByPhone(anyString());
            then(memberRepository).should(never()).findByUsername(anyString());
        }

        @Test
        @DisplayName("성공 - 숫자만으로 된 아이디는 전화번호로 조회")
        void success_phone_login_digits_only() {
            String phone = "01012345678";
            Member mockMember = buildMockMember(2L, "phoneuser");
            given(memberRepository.findByPhone(phone)).willReturn(Optional.of(mockMember));

            Member result = memberService.findByLoginId(phone);

            assertThat(result).isEqualTo(mockMember);
            then(memberRepository).should().findByPhone(phone);
            then(memberRepository).should(never()).findByEmail(anyString());
            then(memberRepository).should(never()).findByUsername(anyString());
        }

        @Test
        @DisplayName("성공 - 숫자와 대시(-)로 된 아이디는 전화번호로 조회 (정규식 ^[0-9\\-]+$ 검증)")
        void success_phone_login_with_dashes() {
            String phone = "010-1234-5678";
            Member mockMember = buildMockMember(3L, "dashuser");
            given(memberRepository.findByPhone(phone)).willReturn(Optional.of(mockMember));

            Member result = memberService.findByLoginId(phone);

            assertThat(result).isEqualTo(mockMember);
            then(memberRepository).should().findByPhone(phone);
            then(memberRepository).should(never()).findByUsername(anyString());
        }

        @Test
        @DisplayName("성공 - 이메일도 전화번호도 아닌 아이디는 username으로 조회")
        void success_username_login() {
            String username = "my_username";
            Member mockMember = buildMockMember(4L, username);
            given(memberRepository.findByUsername(username)).willReturn(Optional.of(mockMember));

            Member result = memberService.findByLoginId(username);

            assertThat(result).isEqualTo(mockMember);
            then(memberRepository).should().findByUsername(username);
            then(memberRepository).should(never()).findByEmail(anyString());
            then(memberRepository).should(never()).findByPhone(anyString());
        }

        @Test
        @DisplayName("실패 - 이메일 형식이지만 DB에 없는 경우 INVALID_CREDENTIALS 예외")
        void fail_email_not_found() {
            given(memberRepository.findByEmail("notexist@test.com")).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.findByLoginId("notexist@test.com"))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.INVALID_CREDENTIALS.getMessage());
        }

        @Test
        @DisplayName("실패 - 전화번호 형식이지만 DB에 없는 경우 INVALID_CREDENTIALS 예외")
        void fail_phone_not_found() {
            given(memberRepository.findByPhone("01099999999")).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.findByLoginId("01099999999"))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.INVALID_CREDENTIALS.getMessage());
        }

        @Test
        @DisplayName("실패 - username 형식이지만 DB에 없는 경우 INVALID_CREDENTIALS 예외")
        void fail_username_not_found() {
            given(memberRepository.findByUsername("ghost_user")).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.findByLoginId("ghost_user"))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.INVALID_CREDENTIALS.getMessage());
        }

        @Test
        @DisplayName("보안 - 이메일·전화번호·username 모두 동일한 INVALID_CREDENTIALS 반환 (사용자 열거 공격 방지)")
        void all_login_failures_return_same_error_code() {
            given(memberRepository.findByEmail(anyString())).willReturn(Optional.empty());
            given(memberRepository.findByPhone(anyString())).willReturn(Optional.empty());
            given(memberRepository.findByUsername(anyString())).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.findByLoginId("ghost@test.com"))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.INVALID_CREDENTIALS.getMessage());

            assertThatThrownBy(() -> memberService.findByLoginId("01000000000"))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.INVALID_CREDENTIALS.getMessage());

            assertThatThrownBy(() -> memberService.findByLoginId("unknown_user"))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.INVALID_CREDENTIALS.getMessage());
        }
    }

    // ============================================================
    // findById()
    // ============================================================

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("성공 - 존재하는 회원 ID로 조회 시 Member 반환")
        void success() {
            Member mockMember = Member.builder().username("test").build();
            given(memberRepository.findById(1L)).willReturn(Optional.of(mockMember));

            Member result = memberService.findById(1L);

            assertThat(result.getUsername()).isEqualTo("test");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 회원 ID → MEMBER_NOT_FOUND 예외")
        void fail_member_not_found() {
            given(memberRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.findById(999L))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("findByUsername()")
    class FindByUsername {

        @Test
        @DisplayName("성공 - 존재하는 username으로 조회 시 Member 반환")
        void success() {
            Member mockMember = buildMockMember(1L, "target_user");
            given(memberRepository.findByUsername("target_user")).willReturn(Optional.of(mockMember));

            Member result = memberService.findByUsername("target_user");

            assertThat(result.getUsername()).isEqualTo("target_user");
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 username → MEMBER_NOT_FOUND 예외")
        void fail_member_not_found() {
            given(memberRepository.findByUsername("ghost_user")).willReturn(Optional.empty());

            assertThatThrownBy(() -> memberService.findByUsername("ghost_user"))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(MemberErrorCode.MEMBER_NOT_FOUND.getMessage());
        }
    }

    // ============================================================
    // checkDuplicate()
    // ============================================================

    @Nested
    @DisplayName("checkDuplicate()")
    class CheckDuplicate {

        @Test
        @DisplayName("username - 이미 존재하면 false (사용 불가)")
        void username_already_exists_returns_false() {
            given(memberRepository.existsByUsername("taken_user")).willReturn(true);

            boolean result = memberService.checkDuplicate("username", "taken_user");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("username - 존재하지 않으면 true (사용 가능)")
        void username_available_returns_true() {
            given(memberRepository.existsByUsername("available_user")).willReturn(false);

            boolean result = memberService.checkDuplicate("username", "available_user");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("email - 이미 존재하면 false (사용 불가)")
        void email_already_exists_returns_false() {
            given(memberRepository.existsByEmail("taken@test.com")).willReturn(true);

            boolean result = memberService.checkDuplicate("email", "taken@test.com");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("email - 존재하지 않으면 true (사용 가능)")
        void email_available_returns_true() {
            given(memberRepository.existsByEmail("free@test.com")).willReturn(false);

            boolean result = memberService.checkDuplicate("email", "free@test.com");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("phone - 이미 존재하면 false (사용 불가)")
        void phone_already_exists_returns_false() {
            given(memberRepository.existsByPhone("01012345678")).willReturn(true);

            boolean result = memberService.checkDuplicate("phone", "01012345678");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("phone - 존재하지 않으면 true (사용 가능)")
        void phone_available_returns_true() {
            given(memberRepository.existsByPhone("01099999999")).willReturn(false);

            boolean result = memberService.checkDuplicate("phone", "01099999999");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("실패 - 지원하지 않는 type → INVALID_INPUT_VALUE 예외")
        void invalid_type_throws_exception() {
            assertThatThrownBy(() -> memberService.checkDuplicate("invalid", "value"))
                    .isInstanceOf(MemberException.class)
                    .hasMessage(CommonErrorCode.INVALID_INPUT_VALUE.getMessage());
        }
    }

    // ============================================================
    // getReferenceById()
    // ============================================================

    @Nested
    @DisplayName("getReferenceById()")
    class GetReferenceById {

        @Test
        @DisplayName("DB 조회 없이 Proxy 객체 반환을 위해 Repository.getReferenceById에 위임")
        void delegates_to_repository() {
            Member proxyMember = Member.builder().username("proxy_user").build();
            given(memberRepository.getReferenceById(5L)).willReturn(proxyMember);

            Member result = memberService.getReferenceById(5L);

            assertThat(result).isEqualTo(proxyMember);
            then(memberRepository).should().getReferenceById(5L);
        }
    }
}
