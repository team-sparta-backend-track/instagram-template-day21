package com.example.instagramclone.domain.auth.api;

import com.example.instagramclone.domain.member.application.MemberService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // application-test.yml 로드: JWT 키 고정값, H2 인메모리 DB
@Transactional // 테스트 데이터 롤백 보장
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        // given: 실제 비즈니스 로직(MemberService)을 통해 테스트용 유저 세팅
        SignUpRequest signUpRequest = SignUpRequest.builder()
                .username("integration_user")
                .emailOrPhone("inter@test.com")
                .password("password!23")
                .name("Integration Test")
                .build();
        memberService.createMember(signUpRequest);
    }

    @Test
    @DisplayName("로그인 API 성공 흐름 검증 - SpringBootTest 통합 테스트")
    void login_api_success() throws Exception {
        // [TDD Step 5] 진짜 클라이언트가 호출하듯이 모든 레이어(Controller -> Service -> DB)를 관통하는 통합 테스트입니다.
        // given
        LoginRequest request = new LoginRequest("inter@test.com", "password!23"); // email 분기 테스트
        String content = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokens.accessToken").exists())
                .andExpect(jsonPath("$.data.tokens.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.user.username").value("integration_user"))
                .andExpect(jsonPath("$.data.user.name").value("Integration Test"));
    }

    @Test
    @DisplayName("로그인 API 실패 흐름 검증 - 존재하지 않는 회원")
    void login_api_fail_user_not_found() throws Exception {
        // [TDD Step 5.1] 존재하지 않는 회원(email)으로 로그인 시도
        // given
        LoginRequest request = new LoginRequest("notfound@test.com", "password!23");
        String content = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isUnauthorized()) // INVALID_CREDENTIALS는 HTTP 401을 반환해야 합니다.
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.status").value(401))
                .andExpect(jsonPath("$.error.code").value("M005"))
                .andExpect(jsonPath("$.error.message").value("아이디 또는 비밀번호가 일치하지 않습니다."))
                .andExpect(jsonPath("$.error.path").value("/api/auth/login"));
    }

    @Test
    @DisplayName("로그인 API 실패 흐름 검증 - 비밀번호 불일치")
    void login_api_fail_invalid_password() throws Exception {
        // [TDD Step 5.2] DB에 등록된 회원이지만 비밀번호가 틀린 경우
        // given
        LoginRequest request = new LoginRequest("inter@test.com", "wrongpassword!23");
        String content = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isUnauthorized()) // 보안상 존재하지 않는 회원과 동일하게 401 에러를 내려줍니다.
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.status").value(401))
                .andExpect(jsonPath("$.error.code").value("M005"))
                .andExpect(jsonPath("$.error.message").value("아이디 또는 비밀번호가 일치하지 않습니다."))
                .andExpect(jsonPath("$.error.path").value("/api/auth/login"));
    }

    // ===============================================
    // 회원가입(SignUp) API 통합 테스트
    // ===============================================

    @Test
    @DisplayName("회원가입 API 성공 흐름 검증 (201 Created)")
    void signup_api_success() throws Exception {
        // [TDD Step] 정상적인 회원가입 요청
        // given
        SignUpRequest request = SignUpRequest.builder()
                .username("new_signup_user")
                .password("Password123!") // 대소문자, 숫자, 특수문자 포함
                .emailOrPhone("newuser@test.com")
                .name("New User")
                .build();
        String content = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isCreated()) // 201 Created
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("new_signup_user"))
                .andExpect(jsonPath("$.data.message").value("회원가입이 완료되었습니다."));
    }

    @Test
    @DisplayName("회원가입 API 실패 검증 - 중복 가입 시도 (400 Bad Request)")
    void signup_api_fail_duplicate() throws Exception {
        // [TDD Step] 기존에 가입된 유저 정보로 다시 가입을 시도할 때 예외 발생 검증
        // given
        // setUp() 에서 생성된 "integration_user" 와 동일한 username 사용
        SignUpRequest request = SignUpRequest.builder()
                .username("integration_user")
                .password("ValidPass123!")
                .emailOrPhone("other_email@test.com")
                .name("Duplicate User")
                .build();
        String content = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest()) // MemberErrorCode.DUPLICATE_USERNAME (HTTP 400)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.status").value(400))
                .andExpect(jsonPath("$.error.code").value("M002"))
                .andExpect(jsonPath("$.error.message").value("이미 존재하는 사용자 이름입니다."))
                .andExpect(jsonPath("$.error.path").value("/api/auth/signup"));
    }

    @Test
    @DisplayName("회원가입 API 실패 검증 - 유효성 검사 실패 (@Valid)")
    void signup_api_fail_validation() throws Exception {
        // [TDD Step] 비밀번호 포맷 등 Bean Validation(@Valid) 처리 검증
        // given
        SignUpRequest request = SignUpRequest.builder()
                .username("usr") // 4자 미만이라 통과 실패
                .password("1234") // 규칙 불만족이라 통과 실패
                .emailOrPhone("invalid_test.com")
                .name("")
                .build();
        String content = objectMapper.writeValueAsString(request);

        // when & then
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isBadRequest()) // MethodArgumentNotValidException 발생하여 400 반환
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.status").value(400))
                .andExpect(jsonPath("$.error.code").value("C001")) // CommonErrorCode.INVALID_INPUT_VALUE
                .andExpect(jsonPath("$.error.path").value("/api/auth/signup"));
    }

    // ===============================================
    // 중복 확인(check-duplicate) API 통합 테스트
    // ===============================================

    @Test
    @DisplayName("중복 확인 API - 사용 가능한 username 조회 시 available: true 반환")
    void check_duplicate_username_available() throws Exception {
        // setUp()에서 "integration_user" 생성됨 → 전혀 다른 username은 사용 가능
        mockMvc.perform(get("/api/auth/check-duplicate")
                        .param("type", "username")
                        .param("value", "brand_new_user_xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.message").value("사용 가능한 username입니다."));
    }

    @Test
    @DisplayName("중복 확인 API - 이미 사용 중인 username 조회 시 available: false 반환")
    void check_duplicate_username_taken() throws Exception {
        // setUp()에서 생성된 "integration_user" 로 조회
        mockMvc.perform(get("/api/auth/check-duplicate")
                        .param("type", "username")
                        .param("value", "integration_user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.message").value("이미 사용 중인 username입니다."));
    }

    @Test
    @DisplayName("중복 확인 API - 사용 가능한 email 조회 시 available: true 반환")
    void check_duplicate_email_available() throws Exception {
        mockMvc.perform(get("/api/auth/check-duplicate")
                        .param("type", "email")
                        .param("value", "nobody@never.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    @DisplayName("중복 확인 API - 이미 등록된 email 조회 시 available: false 반환")
    void check_duplicate_email_taken() throws Exception {
        // setUp()에서 "inter@test.com"으로 가입됨
        mockMvc.perform(get("/api/auth/check-duplicate")
                        .param("type", "email")
                        .param("value", "inter@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    @DisplayName("중복 확인 API - 지원하지 않는 type 요청 시 400 Bad Request 반환")
    void check_duplicate_invalid_type_returns_400() throws Exception {
        mockMvc.perform(get("/api/auth/check-duplicate")
                        .param("type", "invalid_type")
                        .param("value", "somevalue"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
